namespace Puj.Analytics.Models;

public class StudentNameCache
{
    public Guid     Id          { get; set; } = Guid.NewGuid();
    public Guid     UserId      { get; set; }
    public string   StudentName { get; set; } = string.Empty;
    public DateTime UpdatedAt   { get; set; } = DateTime.UtcNow;
}
