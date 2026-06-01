namespace Puj.Analytics.Messages;

/// <summary>
/// Mensaje publicado cuando un estudiante completa una lección.
///
/// <para>
/// Corresponde al evento <c>LESSON_COMPLETED</c> del exchange
/// <c>platform.events</c>. Consumido por
/// <see cref="Puj.Analytics.Consumers.LessonCompletedConsumer"/>.
/// Actualmente reservado para uso futuro; la lógica de certificación
/// se gestiona en <see cref="Puj.Analytics.Consumers.AssessmentSubmittedConsumer"/>.
/// </para>
/// </summary>
/// <param name="EventId">Identificador único del evento (UUID).</param>
/// <param name="EventType">
/// Tipo del evento; siempre <c>"LESSON_COMPLETED"</c>.
/// </param>
/// <param name="UserId">Identificador del estudiante que completó la lección.</param>
/// <param name="LessonId">Identificador de la lección completada.</param>
/// <param name="CourseId">
/// Identificador del curso al que pertenece la lección.
/// </param>
/// <param name="OccurredAt">Fecha y hora (UTC) del evento.</param>
public record LessonCompletedMessage(
    string   EventId,
    string   EventType,
    string   UserId,
    string   LessonId,
    string   CourseId,
    DateTime OccurredAt
);
