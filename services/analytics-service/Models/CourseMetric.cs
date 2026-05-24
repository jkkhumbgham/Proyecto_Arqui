namespace Puj.Analytics.Models;

public class CourseMetric
{
    public Guid     Id               { get; set; } = Guid.NewGuid();
    public Guid     CourseId         { get; set; }
    public string   CourseTitle      { get; set; } = string.Empty;
    public long     TotalEnrollments { get; set; }
    public long     TotalSubmissions { get; set; }
    public decimal  RawScoreSum      { get; set; }
    public decimal  RawMaxScoreSum   { get; set; }
    public long     PassCount        { get; set; }
    public DateTime UpdatedAt        { get; set; } = DateTime.UtcNow;

    public decimal AverageScore => RawMaxScoreSum > 0 ? Math.Round(RawScoreSum / RawMaxScoreSum * 100, 2) : 0m;
    public decimal PassRate     => TotalSubmissions > 0 ? Math.Round((decimal)PassCount * 100 / TotalSubmissions, 2) : 0m;
}
