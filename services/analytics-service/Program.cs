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

var constructor = WebApplication.CreateBuilder(args);

// ─── Database ─────────────────────────────────────────────────────────────────
constructor.Services.AddDbContext<ContextoBaseDatosAnaliticas>(opciones =>
    opciones.UseNpgsql(constructor.Configuration.GetConnectionString("DefaultConnection")));

// ─── JWT Authentication ────────────────────────────────────────────────────────
var clavePublicaJwtPem = constructor.Configuration["Jwt:PublicKey"]
    ?? Environment.GetEnvironmentVariable("JWT_PUBLIC_KEY")
    ?? throw new InvalidOperationException("JWT_PUBLIC_KEY not configured.");

var rsa = RSA.Create();
rsa.ImportFromPem(clavePublicaJwtPem.Replace("\\n", "\n"));

constructor.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(opciones => {
        opciones.TokenValidationParameters = new TokenValidationParameters {
            ValidateIssuer           = true,
            ValidIssuer              = "puj-learning-platform",
            ValidateAudience         = false,
            ValidateLifetime         = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey         = new RsaSecurityKey(rsa),
            ClockSkew                = TimeSpan.FromSeconds(30)
        };
    });

constructor.Services.AddAuthorization();

// ─── Services ─────────────────────────────────────────────────────────────────
constructor.Services.AddHostedService<TrabajoResumenMensual>();

// ─── MassTransit / RabbitMQ ────────────────────────────────────────────────────
constructor.Services.AddMassTransit(x => {
    x.AddConsumer<ConsumidorEvaluacionEntregada>();
    x.AddConsumer<ConsumidorMatriculaCurso>();
    x.AddConsumer<ConsumidorUsuarioRegistrado>();
    x.AddConsumer<ConsumidorUsuarioConectado>();
    x.AddConsumer<ConsumidorLeccionCompletada>();

    x.UsingRabbitMq((ctx, cfg) => {
        cfg.Host(
            constructor.Configuration["RabbitMQ:Host"] ?? "localhost",
            ushort.Parse(constructor.Configuration["RabbitMQ:Port"] ?? "5672"),
            constructor.Configuration["RabbitMQ:VirtualHost"] ?? "/",
            h => {
                h.Username(constructor.Configuration["RabbitMQ:Username"] ?? "guest");
                h.Password(constructor.Configuration["RabbitMQ:Password"] ?? "guest");
            });

        // Un único endpoint — todos los mensajes del exchange platform.events
        // con routing key analytics.# llegan aquí. NO llamar ConfigureEndpoints:
        // ese método crea colas adicionales con nombres automáticos que no reciben nada.
        cfg.ReceiveEndpoint("analytics.results", e => {
            e.SetQueueArgument("x-message-ttl", 86400000L);
            e.SetQueueArgument("x-dead-letter-exchange", "platform.dlx");
            e.ConfigureConsumer<ConsumidorEvaluacionEntregada>(ctx);
            e.ConfigureConsumer<ConsumidorMatriculaCurso>(ctx);
            e.ConfigureConsumer<ConsumidorUsuarioRegistrado>(ctx);
            e.ConfigureConsumer<ConsumidorUsuarioConectado>(ctx);
            e.ConfigureConsumer<ConsumidorLeccionCompletada>(ctx);
            e.UseMessageRetry(r => r.Intervals(500, 1000, 2000));
        });
    });
});

// Esperar hasta 5 min a que RabbitMQ esté listo (K8s: race condition en arranque).
// Si falla dentro de StartTimeout, el pod falla y K8s lo reinicia — ciclo seguro.
constructor.Services.Configure<MassTransitHostOptions>(opciones => {
    opciones.WaitUntilStarted = true;
    opciones.StartTimeout    = TimeSpan.FromSeconds(300); // 5 minutos
    opciones.StopTimeout     = TimeSpan.FromSeconds(30);
});

// ─── Swagger / OpenAPI ─────────────────────────────────────────────────────────
constructor.Services.AddEndpointsApiExplorer();
constructor.Services.AddSwaggerGen(c => {
    c.SwaggerDoc("v1", new() {
        Title   = "Analytics Service API",
        Version = "v1",
        Description = "Reportes y analítica institucional — PUJ Learning Platform"
    });
});

var app = constructor.Build();

// ─── Migrations ────────────────────────────────────────────────────────────────
using (var alcance = app.Services.CreateScope()) {
    var baseDatos = alcance.ServiceProvider.GetRequiredService<ContextoBaseDatosAnaliticas>();
    var log       = alcance.ServiceProvider.GetRequiredService<ILogger<Program>>();
    try {
        baseDatos.Database.EnsureCreated();
        log.LogInformation("Analytics schema ready.");

        // Migración idempotente: añade columnas nuevas si no existen.
        // EnsureCreated() crea las tablas la primera vez pero no altera las existentes.
        baseDatos.Database.ExecuteSqlRaw(@"
            ALTER TABLE analytics.student_name_cache
                ADD COLUMN IF NOT EXISTS email       VARCHAR(200) NOT NULL DEFAULT '',
                ADD COLUMN IF NOT EXISTS role        VARCHAR(50)  NOT NULL DEFAULT '',
                ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
        ");

        if (!baseDatos.EstadisticasPlataforma.Any()) {
            baseDatos.EstadisticasPlataforma.Add(new Puj.Analytics.Models.EstadisticasPlataforma());
            baseDatos.SaveChanges();
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
