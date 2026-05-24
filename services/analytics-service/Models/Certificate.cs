namespace Puj.Analytics.Models;

public class Certificate
{
    public Guid     Id               { get; set; } = Guid.NewGuid();
    public Guid     StudentId        { get; set; }
    public Guid     CourseId         { get; set; }
    public string   CourseTitle      { get; set; } = string.Empty;
    public string   StudentName      { get; set; } = string.Empty;
    public string   InstructorName   { get; set; } = string.Empty;
    public Guid     VerificationCode { get; set; } = Guid.NewGuid();
    public string   PdfUrl           { get; set; } = string.Empty;
    public DateTime IssuedAt         { get; set; } = DateTime.UtcNow;
}
