namespace Puj.Analytics.Models;

public class UserRecord
{
    public Guid     Id          { get; set; } = Guid.NewGuid();
    public Guid     UserId      { get; set; }
    public string   Email       { get; set; } = string.Empty;
    public string   Role        { get; set; } = string.Empty;
    public DateTime RegisteredAt{ get; set; } = DateTime.UtcNow;
    public DateTime CreatedAt   { get; set; } = DateTime.UtcNow;
}
