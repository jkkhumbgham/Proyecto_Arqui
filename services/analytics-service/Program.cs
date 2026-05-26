using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Npgsql;
using Puj.Analytics.Data;
using Puj.Analytics.Consumers;
using Puj.Analytics.Endpoints;
using Puj.Analytics.Exports;
using Puj.Analytics.Services;
using System.Security.Cryptography;

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

// ─── Services ─────────────────────────────────────────────────────────────────
builder.Services.AddHostedService<MonthlySnapshotJob>();

// ─── MassTransit / RabbitMQ ────────────────────────────────────────────────────
builder.Services.AddMassTransit(x => {
    x.AddConsumer<AssessmentSubmittedConsumer>();
    x.AddConsumer<CourseEnrolledConsumer>();
    x.AddConsumer<UserRegisteredConsumer>();
    x.AddConsumer<UserLoggedInConsumer>();
    x.AddConsumer<LessonCompletedConsumer>();

    x.UsingRabbitMq((ctx, cfg) => {
        cfg.Host(
            builder.Configuration["RabbitMQ:Host"] ?? "localhost",
            ushort.Parse(builder.Configuration["RabbitMQ:Port"] ?? "5672"),
            builder.Configuration["RabbitMQ:VirtualHost"] ?? "/",
            h => {
                h.Username(builder.Configuration["RabbitMQ:Username"] ?? "guest");
                h.Password(builder.Configuration["RabbitMQ:Password"] ?? "guest");
            });

        // Un único endpoint — todos los mensajes del exchange platform.events
        // con routing key analytics.# llegan aquí. NO llamar ConfigureEndpoints:
        // ese método crea colas adicionales con nombres automáticos que no reciben nada.
        cfg.ReceiveEndpoint("analytics.results", e => {
            e.SetQueueArgument("x-message-ttl", 86400000L);
            e.SetQueueArgument("x-dead-letter-exchange", "platform.dlx");
            e.ConfigureConsumer<AssessmentSubmittedConsumer>(ctx);
            e.ConfigureConsumer<CourseEnrolledConsumer>(ctx);
            e.ConfigureConsumer<UserRegisteredConsumer>(ctx);
            e.ConfigureConsumer<UserLoggedInConsumer>(ctx);
            e.ConfigureConsumer<LessonCompletedConsumer>(ctx);
            e.UseMessageRetry(r => r.Intervals(500, 1000, 2000));
        });
    });
});

// Esperar hasta 5 min a que RabbitMQ esté listo (K8s: race condition en arranque).
// Si falla dentro de StartTimeout, el pod falla y K8s lo reinicia — ciclo seguro.
builder.Services.Configure<MassTransitHostOptions>(options => {
    options.WaitUntilStarted = true;
    options.StartTimeout    = TimeSpan.FromSeconds(300); // 5 minutos
    options.StopTimeout     = TimeSpan.FromSeconds(30);
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
    var db  = scope.ServiceProvider.GetRequiredService<AnalyticsDbContext>();
    var log = scope.ServiceProvider.GetRequiredService<ILogger<Program>>();
    try {
        db.Database.EnsureCreated();
        log.LogInformation("Analytics schema ready.");

        // Migración idempotente: añade columnas nuevas si no existen.
        // EnsureCreated() crea las tablas la primera vez pero no altera las existentes.
        db.Database.ExecuteSqlRaw(@"
            ALTER TABLE analytics.student_name_cache
                ADD COLUMN IF NOT EXISTS email       VARCHAR(200) NOT NULL DEFAULT '',
                ADD COLUMN IF NOT EXISTS role        VARCHAR(50)  NOT NULL DEFAULT '',
                ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
        ");

        if (!db.PlatformStats.Any()) {
            db.PlatformStats.Add(new Puj.Analytics.Models.PlatformStats());
            db.SaveChanges();
            log.LogInformation("platform_stats singleton creado.");
        }
    } catch (Exception ex) {
        log.LogWarning(ex, "Analytics schema initialization warning (tables may already exist).");
    }
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
app.MapMonthlySnapshotEndpoints();

app.MapGet("/health", () => Results.Ok(new { status = "UP", timestamp = DateTime.UtcNow }))
   .WithTags("Health");

app.Run();
