using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;

namespace Puj.Analytics.Consumers;

/// <summary>
/// Consumer de MassTransit que procesa el evento <c>USER_LOGGED_IN</c>.
///
/// <para>
/// Actualiza el campo <c>last_login_at</c> en el caché local de usuarios
/// (<see cref="Puj.Analytics.Models.StudentNameCache"/>) para permitir
/// el reporte de inactividad del endpoint <c>/dashboard/inactive-users</c>.
/// Si el usuario no existe en caché (registro previo a que analytics
/// arrancara), crea el registro con los datos disponibles en el evento.
/// </para>
/// </summary>
/// <param name="db">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class UserLoggedInConsumer(
    AnalyticsDbContext db,
    ILogger<UserLoggedInConsumer> logger)
    : IConsumer<UserLoggedInMessage>
{
    /// <summary>
    /// Procesa un mensaje <see cref="UserLoggedInMessage"/> recibido desde
    /// RabbitMQ y actualiza <c>last_login_at</c> del usuario en el caché local.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea que representa la operación asíncrona.</returns>
    public async Task Consume(ConsumeContext<UserLoggedInMessage> context)
    {
        var msg    = context.Message;
        var userId = Guid.Parse(msg.UserId);
        logger.LogInformation("Processing USER_LOGGED_IN: {UserId}", msg.UserId);

        var cache = await db.StudentNameCaches
            .FirstOrDefaultAsync(c => c.UserId == userId);

        if (cache == null)
        {
            // El registro puede no existir si el usuario se registró antes de
            // que analytics arrancara y el evento USER_REGISTERED no fue procesado.
            cache = new Puj.Analytics.Models.StudentNameCache
            {
                UserId = userId,
                Email  = msg.Email ?? string.Empty,
                Role   = msg.Role  ?? string.Empty
            };
            db.StudentNameCaches.Add(cache);
        }

        cache.LastLoginAt = DateTime.UtcNow;
        cache.UpdatedAt   = DateTime.UtcNow;
        await db.SaveChangesAsync();
    }
}
