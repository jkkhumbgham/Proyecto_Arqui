namespace Puj.Analytics.Models;

public class EnrollmentRecord
{
    public Guid     Id          { get; set; } = Guid.NewGuid();
    public Guid     UserId      { get; set; }
    public Guid     CourseId    { get; set; }
    public string   CourseTitle { get; set; } = string.Empty;
    public DateTime OccurredAt  { get; set; } = DateTime.UtcNow;
    public DateTime CreatedAt   { get; set; } = DateTime.UtcNow;
}
