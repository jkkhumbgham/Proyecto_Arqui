namespace Puj.Analytics.Messages;

/// <summary>
/// Mensaje publicado cuando un estudiante entrega una evaluación.
///
/// <para>
/// Corresponde al evento <c>ASSESSMENT_SUBMITTED</c> del exchange
/// <c>platform.events</c>. Consumido por
/// <see cref="Puj.Analytics.Consumers.AssessmentSubmittedConsumer"/>.
/// </para>
/// </summary>
/// <param name="EventId">Identificador único del evento (UUID).</param>
/// <param name="EventType">
/// Tipo del evento; siempre <c>"ASSESSMENT_SUBMITTED"</c>.
/// </param>
/// <param name="SubmissionId">Identificador de la entrega.</param>
/// <param name="UserId">Identificador del estudiante que entregó.</param>
/// <param name="AssessmentId">Identificador de la evaluación respondida.</param>
/// <param name="CourseId">
/// Identificador del curso al que pertenece la evaluación.
/// </param>
/// <param name="LessonId">
/// Identificador de la lección asociada, o <c>null</c> si es evaluación
/// de nivel de curso.
/// </param>
/// <param name="Score">Puntaje obtenido por el estudiante.</param>
/// <param name="MaxScore">Puntaje máximo posible para la evaluación.</param>
/// <param name="Passed">
/// <c>true</c> si el estudiante superó el umbral de aprobación.
/// </param>
/// <param name="DurationSeconds">
/// Tiempo en segundos que tardó el estudiante en completar la evaluación.
/// </param>
/// <param name="OccurredAt">Fecha y hora (UTC) del evento.</param>
/// <param name="AllAssessmentsPassed">
/// <c>true</c> si el estudiante ha aprobado todas las evaluaciones del curso.
/// </param>
public record AssessmentSubmittedMessage(
    string   EventId,
    string   EventType,
    string   SubmissionId,
    string   UserId,
    string   AssessmentId,
    string   CourseId,
    string?  LessonId,
    decimal  Score,
    decimal  MaxScore,
    bool     Passed,
    long     DurationSeconds,
    DateTime OccurredAt,
    bool     AllAssessmentsPassed
);
