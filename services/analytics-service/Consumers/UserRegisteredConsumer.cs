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
/// Incrementa el contador <c>total_users</c> en <see cref="EstadisticasPlataforma"/>
/// y crea o actualiza la entrada del usuario en el caché local
/// <see cref="CacheNombreEstudiante"/> con nombre completo, correo y rol.
/// </para>
/// </summary>
/// <param name="baseDatos">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class ConsumidorUsuarioRegistrado(
    ContextoBaseDatosAnaliticas baseDatos,
    ILogger<ConsumidorUsuarioRegistrado> logger)
    : IConsumer<UserRegisteredMessage>
{
    /// <summary>
    /// Procesa un mensaje <see cref="UserRegisteredMessage"/> recibido desde
    /// RabbitMQ, incrementa el total de usuarios en <see cref="EstadisticasPlataforma"/>
    /// y persiste los datos del usuario en <see cref="CacheNombreEstudiante"/>.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea que representa la operación asíncrona.</returns>
    public async Task Consume(ConsumeContext<UserRegisteredMessage> context)
    {
        var mensaje = context.Message;
        logger.LogInformation("Processing USER_REGISTERED: {UserId}", mensaje.UserId);

        var estadisticas = await baseDatos.EstadisticasPlataforma.FirstOrDefaultAsync();
        if (estadisticas == null)
        {
            estadisticas = new EstadisticasPlataforma();
            baseDatos.EstadisticasPlataforma.Add(estadisticas);
        }

        estadisticas.TotalUsuarios++;
        estadisticas.ActualizadoEn = DateTime.UtcNow;

        var idUsuario    = Guid.Parse(mensaje.UserId);
        var entradaCache = await baseDatos.CachesNombreEstudiante
            .FirstOrDefaultAsync(c => c.IdUsuario == idUsuario);

        if (entradaCache == null)
        {
            entradaCache = new CacheNombreEstudiante { IdUsuario = idUsuario };
            baseDatos.CachesNombreEstudiante.Add(entradaCache);
        }

        entradaCache.NombreEstudiante = $"{mensaje.FirstName} {mensaje.LastName}".Trim();
        entradaCache.Correo           = mensaje.Email ?? string.Empty;
        entradaCache.Rol              = mensaje.Role  ?? string.Empty;
        entradaCache.ActualizadoEn    = DateTime.UtcNow;

        await baseDatos.SaveChangesAsync();
    }
}
