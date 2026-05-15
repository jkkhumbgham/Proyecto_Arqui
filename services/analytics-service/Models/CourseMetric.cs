namespace Puj.Analytics.Models;

public class CourseMetric
{
    public Guid    Id                  { get; set; } = Guid.NewGuid();
    public Guid    CourseId            { get; set; }
    public string  CourseTitle         { get; set; } = string.Empty;
    public int     TotalEnrollments    { get; set; }
    public int     TotalCompletions    { get; set; }
    public decimal AverageScore        { get; set; }
    public decimal PassRate            { get; set; }
    public DateTime UpdatedAt          { get; set; } = DateTime.UtcNow;
}
