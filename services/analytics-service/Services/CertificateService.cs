using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Models;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Puj.Analytics.Services;

public class CertificateService(
    AnalyticsDbContext db,
    MinioStorageService storage,
    ILogger<CertificateService> logger)
{
    public async Task<Certificate?> GetExistingCertificate(Guid studentId, Guid courseId)
        => await db.Certificates.FirstOrDefaultAsync(c => c.StudentId == studentId && c.CourseId == courseId);

    public async Task<Certificate> IssueCertificate(
        Guid studentId, Guid courseId, string courseTitle,
        string studentName, string instructorName)
    {
        var existing = await GetExistingCertificate(studentId, courseId);
        if (existing != null) return existing;

        var cert = new Certificate {
            StudentId      = studentId,
            CourseId       = courseId,
            CourseTitle    = courseTitle,
            StudentName    = studentName,
            InstructorName = instructorName,
            IssuedAt       = DateTime.UtcNow
        };

        byte[] pdf = GeneratePdf(cert);
        string key = $"certificates/{studentId}/{courseId}/{cert.VerificationCode}.pdf";
        cert.PdfUrl = await storage.UploadPdfAsync(key, pdf);

        db.Certificates.Add(cert);
        await db.SaveChangesAsync();

        logger.LogInformation("Certificate issued: {CertId} for student {StudentId} course {CourseId}",
            cert.Id, studentId, courseId);
        return cert;
    }

    private static byte[] GeneratePdf(Certificate cert)
    {
        QuestPDF.Settings.License = LicenseType.Community;

        return Document.Create(container => {
            container.Page(page => {
                page.Size(PageSizes.A4.Landscape());
                page.Margin(40);
                page.Background().Image(Placeholders.Image(1190, 842));

                page.Content().Column(col => {
                    col.Spacing(20);

                    col.Item().AlignCenter().Text("PLATAFORMA DE APRENDIZAJE PUJ")
                        .FontSize(14).FontColor(Colors.Grey.Medium).Bold();

                    col.Item().AlignCenter().Text("Certificado de Finalización")
                        .FontSize(32).Bold().FontColor(Colors.Blue.Darken3);

                    col.Item().AlignCenter().Text("Este certificado acredita que")
                        .FontSize(14).FontColor(Colors.Grey.Darken2);

                    col.Item().AlignCenter().Text(cert.StudentName)
                        .FontSize(26).Bold().FontColor(Colors.Black);

                    col.Item().AlignCenter().Text("ha completado satisfactoriamente el curso")
                        .FontSize(14).FontColor(Colors.Grey.Darken2);

                    col.Item().AlignCenter().Text($"\"{cert.CourseTitle}\"")
                        .FontSize(20).Bold().FontColor(Colors.Blue.Darken2);

                    col.Item().AlignCenter().Text($"Instructor: {cert.InstructorName}")
                        .FontSize(13).FontColor(Colors.Grey.Darken1);

                    col.Item().AlignCenter()
                        .Text($"Expedido el {cert.IssuedAt:dd/MM/yyyy}")
                        .FontSize(12).FontColor(Colors.Grey.Medium);

                    col.Item().AlignCenter()
                        .Text($"Código de verificación: {cert.VerificationCode}")
                        .FontSize(10).FontColor(Colors.Grey.Lighten1);
                });
            });
        }).GeneratePdf();
    }
}
