using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

/// <summary>
/// Consumer de MassTransit que procesa el evento <c>USER_REGISTERED</c>.
///
/// <para>
/// Incrementa el contador <c>total_users</c> en <see cref="PlatformStats"/>
/// y crea o actualiza la entrada del usuario en el caché local
/// <see cref="StudentNameCache"/> con nombre completo, correo y rol.
/// </para>
/// </summary>
/// <param name="db">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class UserRegisteredConsumer(
    AnalyticsDbContext db,
    ILogger<UserRegisteredConsumer> logger)
    : IConsumer<UserRegisteredMessage>
{
    /// <summary>
    /// Procesa un mensaje <see cref="UserRegisteredMessage"/> recibido desde
    /// RabbitMQ, incrementa el total de usuarios en <see cref="PlatformStats"/>
    /// y persiste los datos del usuario en <see cref="StudentNameCache"/>.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea que representa la operación asíncrona.</returns>
    public async Task Consume(ConsumeContext<UserRegisteredMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing USER_REGISTERED: {UserId}", msg.UserId);

        var stats = await db.PlatformStats.FirstOrDefaultAsync();
        if (stats == null)
        {
            stats = new PlatformStats();
            db.PlatformStats.Add(stats);
        }

        stats.TotalUsers++;
        stats.UpdatedAt = DateTime.UtcNow;

        var userId = Guid.Parse(msg.UserId);
        var cache  = await db.StudentNameCaches
            .FirstOrDefaultAsync(c => c.UserId == userId);

        if (cache == null)
        {
            cache = new StudentNameCache { UserId = userId };
            db.StudentNameCaches.Add(cache);
        }

        cache.StudentName = $"{msg.FirstName} {msg.LastName}".Trim();
        cache.Email       = msg.Email ?? string.Empty;
        cache.Role        = msg.Role  ?? string.Empty;
        cache.UpdatedAt   = DateTime.UtcNow;

        await db.SaveChangesAsync();
    }
}
