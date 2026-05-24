namespace Puj.Analytics.Messages;

public record LessonCompletedMessage(
    string   EventId,
    string   EventType,
    string   UserId,
    string   LessonId,
    string   CourseId,
    DateTime OccurredAt
);
