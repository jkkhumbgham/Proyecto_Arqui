namespace Puj.Analytics.Messages;

/// <summary>
/// Mensaje publicado cuando un estudiante se inscribe en un curso.
///
/// <para>
/// Corresponde al evento <c>COURSE_ENROLLED</c> del exchange
/// <c>platform.events</c>. Consumido por
/// <see cref="Puj.Analytics.Consumers.CourseEnrolledConsumer"/>.
/// </para>
/// </summary>
/// <param name="EventId">Identificador único del evento (UUID).</param>
/// <param name="EventType">
/// Tipo del evento; siempre <c>"COURSE_ENROLLED"</c>.
/// </param>
/// <param name="EnrollmentId">Identificador de la inscripción creada.</param>
/// <param name="UserId">Identificador del estudiante que se inscribió.</param>
/// <param name="CourseId">Identificador del curso en el que se inscribió.</param>
/// <param name="CourseTitle">Título legible del curso.</param>
/// <param name="OccurredAt">Fecha y hora (UTC) del evento.</param>
public record CourseEnrolledMessage(
    string   EventId,
    string   EventType,
    string   EnrollmentId,
    string   UserId,
    string   CourseId,
    string   CourseTitle,
    DateTime OccurredAt
);
