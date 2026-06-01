// =============================================================================
// ConsumidorUsuarioRegistradoTests.cs  — analytics-service (xUnit + EF InMemory)
//
// Cubre el caso de prueba unitaria del plan:
//   TU-015: ConsumidorUsuarioRegistrado.Consume() — mensaje válido →
//           StudentNameCache creado en BD y TotalUsuarios incrementado
// =============================================================================
using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Moq;
using Puj.Analytics.Consumers;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;
using Xunit;

namespace Puj.Analytics.Tests;

/// <summary>
/// Pruebas unitarias del consumer <see cref="ConsumidorUsuarioRegistrado"/>.
///
/// <para>
/// Se simula el contexto de MassTransit con un mock de
/// <see cref="ConsumeContext{T}"/> y se usa EF Core InMemory para
/// verificar la persistencia sin base de datos real.
/// </para>
/// </summary>
public class ConsumidorUsuarioRegistradoTests
{
    // ── Helper ─────────────────────────────────────────────────────────────

    private static ContextoBaseDatosAnaliticas CrearContexto()
    {
        var opciones = new DbContextOptionsBuilder<ContextoBaseDatosAnaliticas>()
            .UseInMemoryDatabase(Guid.NewGuid().ToString())
            .Options;
        return new ContextoBaseDatosAnaliticas(opciones);
    }

    // ── TU-015: mensaje válido → cache y contador actualizados ─────────────

    /// <summary>
    /// TU-015 — Cuando se consume un UserRegisteredMessage válido, el consumer
    /// debe crear una entrada en CachesNombreEstudiante con nombre completo,
    /// correo y rol correctos, e incrementar TotalUsuarios en 1.
    /// </summary>
    [Fact]
    public async Task Consumir_MensajeValido_CreaEntradaCacheYIncrementaTotalUsuarios()
    {
        // Arrange
        await using var db = CrearContexto();
        var logger   = NullLogger<ConsumidorUsuarioRegistrado>.Instance;
        var consumer = new ConsumidorUsuarioRegistrado(db, logger);

        var userId  = Guid.NewGuid();
        var mensaje = new UserRegisteredMessage(
            EventId:    Guid.NewGuid().ToString(),
            EventType:  "USER_REGISTERED",
            UserId:     userId.ToString(),
            Email:      "ana.garcia@puj.edu.co",
            FirstName:  "Ana",
            LastName:   "García",
            Role:       "STUDENT",
            OccurredAt: DateTime.UtcNow
        );

        var mockContext = new Mock<ConsumeContext<UserRegisteredMessage>>();
        mockContext.SetupGet(c => c.Message).Returns(mensaje);

        // Act
        await consumer.Consume(mockContext.Object);

        // Assert — verificar caché creado
        var cache = await db.CachesNombreEstudiante
            .FirstOrDefaultAsync(c => c.Correo == "ana.garcia@puj.edu.co");

        Assert.NotNull(cache);
        Assert.Equal("Ana García",              cache.NombreEstudiante);
        Assert.Equal("ana.garcia@puj.edu.co",   cache.Correo);
        Assert.Equal("STUDENT",                 cache.Rol);
        Assert.Equal(userId,                    cache.IdUsuario);

        // Assert — verificar contador de usuarios
        var stats = await db.EstadisticasPlataforma.FirstOrDefaultAsync();
        Assert.NotNull(stats);
        Assert.Equal(1L, stats.TotalUsuarios);
    }

    /// <summary>
    /// TU-015 (múltiples mensajes) — Cada nuevo usuario registrado debe
    /// incrementar TotalUsuarios y crear su propia entrada en el caché.
    /// </summary>
    [Fact]
    public async Task Consumir_TresMensajes_TotalUsuariosEsTres()
    {
        await using var db = CrearContexto();
        var logger   = NullLogger<ConsumidorUsuarioRegistrado>.Instance;
        var consumer = new ConsumidorUsuarioRegistrado(db, logger);

        for (int i = 1; i <= 3; i++)
        {
            var mensaje = new UserRegisteredMessage(
                EventId:    Guid.NewGuid().ToString(),
                EventType:  "USER_REGISTERED",
                UserId:     Guid.NewGuid().ToString(),
                Email:      $"usuario{i}@puj.edu.co",
                FirstName:  $"Usuario{i}",
                LastName:   "Test",
                Role:       "STUDENT",
                OccurredAt: DateTime.UtcNow
            );

            var ctx = new Mock<ConsumeContext<UserRegisteredMessage>>();
            ctx.SetupGet(c => c.Message).Returns(mensaje);
            await consumer.Consume(ctx.Object);
        }

        var stats = await db.EstadisticasPlataforma.FirstOrDefaultAsync();
        var cachesCount = await db.CachesNombreEstudiante.CountAsync();

        Assert.NotNull(stats);
        Assert.Equal(3L, stats.TotalUsuarios);
        Assert.Equal(3,  cachesCount);
    }

    /// <summary>
    /// TU-015 (actualización) — Si el mismo userId llega dos veces,
    /// el caché debe actualizarse en lugar de duplicarse.
    /// </summary>
    [Fact]
    public async Task Consumir_MismoUsuarioDosVeces_ActualizaSinDuplicar()
    {
        await using var db = CrearContexto();
        var logger   = NullLogger<ConsumidorUsuarioRegistrado>.Instance;
        var consumer = new ConsumidorUsuarioRegistrado(db, logger);

        var userId = Guid.NewGuid().ToString();

        var msg1 = new UserRegisteredMessage(
            Guid.NewGuid().ToString(), "USER_REGISTERED",
            userId, "duplicado@puj.edu.co", "Laura", "Garzón", "STUDENT", DateTime.UtcNow);

        var msg2 = new UserRegisteredMessage(
            Guid.NewGuid().ToString(), "USER_REGISTERED",
            userId, "duplicado@puj.edu.co", "Laura", "Garzón Actualizado", "INSTRUCTOR", DateTime.UtcNow);

        var ctx1 = new Mock<ConsumeContext<UserRegisteredMessage>>();
        ctx1.SetupGet(c => c.Message).Returns(msg1);
        await consumer.Consume(ctx1.Object);

        var ctx2 = new Mock<ConsumeContext<UserRegisteredMessage>>();
        ctx2.SetupGet(c => c.Message).Returns(msg2);
        await consumer.Consume(ctx2.Object);

        // Solo debe existir una entrada en el caché para ese userId
        var caches = await db.CachesNombreEstudiante
            .Where(c => c.IdUsuario == Guid.Parse(userId))
            .ToListAsync();

        Assert.Single(caches);
        // El nombre debe haberse actualizado con el segundo mensaje
        Assert.Equal("Laura Garzón Actualizado", caches[0].NombreEstudiante);
        Assert.Equal("INSTRUCTOR", caches[0].Rol);
    }

    /// <summary>
    /// TU-015 (nombre completo) — El nombre completo debe formarse como
    /// "FirstName LastName" sin espacios extra cuando algún campo está vacío.
    /// </summary>
    [Fact]
    public async Task Consumir_NombreSoloSinApellido_NombreCompletoEsNombreSolo()
    {
        await using var db = CrearContexto();
        var consumer = new ConsumidorUsuarioRegistrado(db, NullLogger<ConsumidorUsuarioRegistrado>.Instance);

        var mensaje = new UserRegisteredMessage(
            Guid.NewGuid().ToString(), "USER_REGISTERED",
            Guid.NewGuid().ToString(), "sinApellido@puj.edu.co",
            "Arley", "", "ADMIN", DateTime.UtcNow);

        var ctx = new Mock<ConsumeContext<UserRegisteredMessage>>();
        ctx.SetupGet(c => c.Message).Returns(mensaje);
        await consumer.Consume(ctx.Object);

        var cache = await db.CachesNombreEstudiante
            .FirstOrDefaultAsync(c => c.Correo == "sinApellido@puj.edu.co");

        Assert.NotNull(cache);
        // Trim() elimina el espacio sobrante cuando LastName es vacío
        Assert.Equal("Arley", cache.NombreEstudiante);
    }
}
