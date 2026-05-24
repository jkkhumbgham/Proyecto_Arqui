namespace Puj.Analytics.Models;

public class MonthlySnapshot
{
    public Guid     Id                       { get; set; } = Guid.NewGuid();
    public int      Year                     { get; set; }
    public int      Month                    { get; set; }

    // Acumulados hasta el cierre del mes
    public long     TotalUsers               { get; set; }
    public long     TotalEnrollments         { get; set; }
    public long     TotalSubmissions         { get; set; }
    public long     TotalCourses             { get; set; }
    public decimal  AvgScore                 { get; set; }
    public decimal  PassRate                 { get; set; }

    // Actividad nueva durante el mes
    public long     NewUsersThisMonth        { get; set; }
    public long     NewEnrollmentsThisMonth  { get; set; }
    public long     NewSubmissionsThisMonth  { get; set; }

    public DateTime GeneratedAt              { get; set; } = DateTime.UtcNow;
}
