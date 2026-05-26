namespace Puj.Analytics.Models;

public class StudentNameCache
{
    public Guid      Id          { get; set; } = Guid.NewGuid();
    public Guid      UserId      { get; set; }
    public string    StudentName { get; set; } = string.Empty;
    public string    Email       { get; set; } = string.Empty;
    public string    Role        { get; set; } = string.Empty;
    public DateTime? LastLoginAt { get; set; }   // null = nunca inició sesión
    public DateTime  UpdatedAt   { get; set; } = DateTime.UtcNow;
}
