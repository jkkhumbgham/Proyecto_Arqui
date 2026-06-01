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
/// (<see cref="Puj.Analytics.Models.CacheNombreEstudiante"/>) para permitir
/// el reporte de inactividad del endpoint <c>/dashboard/inactive-users</c>.
/// Si el usuario no existe en caché (registro previo a que analytics
/// arrancara), crea el registro con los datos disponibles en el evento.
/// </para>
/// </summary>
/// <param name="baseDatos">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class ConsumidorUsuarioConectado(
    ContextoBaseDatosAnaliticas baseDatos,
    ILogger<ConsumidorUsuarioConectado> logger)
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
        var mensaje  = context.Message;
        var idUsuario = Guid.Parse(mensaje.UserId);
        logger.LogInformation("Processing USER_LOGGED_IN: {UserId}", mensaje.UserId);

        var entradaCache = await baseDatos.CachesNombreEstudiante
            .FirstOrDefaultAsync(c => c.IdUsuario == idUsuario);

        if (entradaCache == null)
        {
            // El registro puede no existir si el usuario se registró antes de
            // que analytics arrancara y el evento USER_REGISTERED no fue procesado.
            entradaCache = new Puj.Analytics.Models.CacheNombreEstudiante
            {
                IdUsuario = idUsuario,
                Correo    = mensaje.Email ?? string.Empty,
                Rol       = mensaje.Role  ?? string.Empty
            };
            baseDatos.CachesNombreEstudiante.Add(entradaCache);
        }

        entradaCache.UltimoAccesoEn = DateTime.UtcNow;
        entradaCache.ActualizadoEn  = DateTime.UtcNow;
        await baseDatos.SaveChangesAsync();
    }
}
