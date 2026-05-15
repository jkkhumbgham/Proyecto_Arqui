namespace Puj.Analytics.Messages;

public record CourseEnrolledMessage(
    string   EventId,
    string   EventType,
    string   EnrollmentId,
    string   UserId,
    string   CourseId,
    string   CourseTitle,
    DateTime OccurredAt
);
