using MassTransit;
using Puj.Analytics.Messages;

namespace Puj.Analytics.Consumers;

/// <summary>
/// Consumer de MassTransit que recibe el evento <c>LESSON_COMPLETED</c>.
///
/// <para>
/// Actualmente reservado para uso futuro. La emisión de certificados y
/// la lógica de finalización de curso se gestionan en
/// <see cref="ConsumidorEvaluacionEntregada"/>. Este consumer solo registra
/// el evento a nivel de depuración.
/// </para>
/// </summary>
/// <param name="logger">Logger estructurado del consumer.</param>
public class ConsumidorLeccionCompletada(ILogger<ConsumidorLeccionCompletada> logger)
    : IConsumer<LessonCompletedMessage>
{
    /// <summary>
    /// Recibe el mensaje <see cref="LessonCompletedMessage"/> y lo registra
    /// en el log de depuración sin realizar ninguna operación de persistencia.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea completada inmediatamente.</returns>
    public Task Consume(ConsumeContext<LessonCompletedMessage> context)
    {
        logger.LogDebug(
            "LESSON_COMPLETED: user={UserId} course={CourseId}",
            context.Message.UserId, context.Message.CourseId);
        return Task.CompletedTask;
    }
}
