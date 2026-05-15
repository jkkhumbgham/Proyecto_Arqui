namespace Puj.Analytics.Models;

public class SubmissionRecord
{
    public Guid     Id               { get; set; } = Guid.NewGuid();
    public Guid     UserId           { get; set; }
    public Guid     AssessmentId     { get; set; }
    public Guid     CourseId         { get; set; }
    public Guid?    LessonId         { get; set; }
    public decimal  Score            { get; set; }
    public decimal  MaxScore         { get; set; }
    public bool     Passed           { get; set; }
    public long     DurationSeconds  { get; set; }
    public int      AttemptNumber    { get; set; }
    public DateTime OccurredAt       { get; set; } = DateTime.UtcNow;
    public DateTime CreatedAt        { get; set; } = DateTime.UtcNow;
}
