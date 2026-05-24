namespace Puj.Analytics.Models;

public class PlatformStats
{
    public Guid     Id              { get; set; } = Guid.NewGuid();
    public long     TotalUsers      { get; set; }
    public long     TotalEnrollments { get; set; }
    public long     TotalSubmissions { get; set; }
    public long     TotalCourses    { get; set; }
    public decimal  RawScoreSum     { get; set; }
    public decimal  RawMaxScoreSum  { get; set; }
    public long     PassCount       { get; set; }
    public DateTime UpdatedAt       { get; set; } = DateTime.UtcNow;

    public decimal AvgScore  => RawMaxScoreSum > 0 ? Math.Round(RawScoreSum / RawMaxScoreSum * 100, 2) : 0m;
    public decimal PassRate  => TotalSubmissions > 0 ? Math.Round((decimal)PassCount * 100 / TotalSubmissions, 2) : 0m;
}
