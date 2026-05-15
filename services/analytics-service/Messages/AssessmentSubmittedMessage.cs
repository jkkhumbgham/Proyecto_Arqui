namespace Puj.Analytics.Messages;

public record AssessmentSubmittedMessage(
    string  EventId,
    string  EventType,
    string  SubmissionId,
    string  UserId,
    string  AssessmentId,
    string  CourseId,
    string? LessonId,
    decimal Score,
    decimal MaxScore,
    bool    Passed,
    long    DurationSeconds,
    DateTime OccurredAt
);
