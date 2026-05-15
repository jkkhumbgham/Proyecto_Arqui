using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Puj.Analytics.Data;
using Puj.Analytics.Consumers;
using Puj.Analytics.Endpoints;
using Puj.Analytics.Exports;
using System.Security.Cryptography;
using System.Text;

var builder = WebApplication.CreateBuilder(args);

// ─── Database ─────────────────────────────────────────────────────────────────
builder.Services.AddDbContext<AnalyticsDbContext>(opts =>
    opts.UseNpgsql(builder.Configuration.GetConnectionString("DefaultConnection")));

// ─── JWT Authentication ────────────────────────────────────────────────────────
var jwtPublicKeyPem = builder.Configuration["Jwt:PublicKey"]
    ?? Environment.GetEnvironmentVariable("JWT_PUBLIC_KEY")
    ?? throw new InvalidOperationException("JWT_PUBLIC_KEY not configured.");

var rsa = RSA.Create();
rsa.ImportFromPem(jwtPublicKeyPem.Replace("\\n", "\n"));

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(opts => {
        opts.TokenValidationParameters = new TokenValidationParameters {
            ValidateIssuer           = true,
            ValidIssuer              = "puj-learning-platform",
            ValidateAudience         = false,
            ValidateLifetime         = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey         = new RsaSecurityKey(rsa),
            ClockSkew                = TimeSpan.FromSeconds(30)
        };
    });

builder.Services.AddAuthorization();

// ─── MassTransit / RabbitMQ ────────────────────────────────────────────────────
builder.Services.AddMassTransit(x => {
    x.AddConsumer<AssessmentSubmittedConsumer>();
    x.AddConsumer<CourseEnrolledConsumer>();
    x.AddConsumer<UserRegisteredConsumer>();

    x.UsingRabbitMq((ctx, cfg) => {
        cfg.Host(
            builder.Configuration["RabbitMQ:Host"] ?? "localhost",
            ushort.Parse(builder.Configuration["RabbitMQ:Port"] ?? "5672"),
            builder.Configuration["RabbitMQ:VirtualHost"] ?? "/",
            h => {
                h.Username(builder.Configuration["RabbitMQ:Username"] ?? "guest");
                h.Password(builder.Configuration["RabbitMQ:Password"] ?? "guest");
            });

        cfg.ReceiveEndpoint("analytics.results", e => {
            e.ConfigureConsumer<AssessmentSubmittedConsumer>(ctx);
            e.ConfigureConsumer<CourseEnrolledConsumer>(ctx);
            e.ConfigureConsumer<UserRegisteredConsumer>(ctx);
            e.UseMessageRetry(r => r.Intervals(500, 1000, 2000));
        });

        cfg.ConfigureEndpoints(ctx);
    });
});

// ─── Swagger / OpenAPI ─────────────────────────────────────────────────────────
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c => {
    c.SwaggerDoc("v1", new() {
        Title   = "Analytics Service API",
        Version = "v1",
        Description = "Reportes y analítica institucional — PUJ Learning Platform"
    });
});

var app = builder.Build();

// ─── Migrations ────────────────────────────────────────────────────────────────
using (var scope = app.Services.CreateScope()) {
    var db = scope.ServiceProvider.GetRequiredService<AnalyticsDbContext>();
    db.Database.Migrate();
}

// ─── Middleware ───────────────────────────────────────────────────────────────
app.UseSwagger();
app.UseSwaggerUI(c => c.SwaggerEndpoint("/swagger/v1/swagger.json", "Analytics API v1"));
app.UseAuthentication();
app.UseAuthorization();

// ─── Endpoints ────────────────────────────────────────────────────────────────
app.MapDashboardEndpoints();
app.MapCourseAnalyticsEndpoints();
app.MapStudentProgressEndpoints();
app.MapExportEndpoints();

app.MapGet("/health", () => Results.Ok(new { status = "UP", timestamp = DateTime.UtcNow }))
   .WithTags("Health");

app.Run();
