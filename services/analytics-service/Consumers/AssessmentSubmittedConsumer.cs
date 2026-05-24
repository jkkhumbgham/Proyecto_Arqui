using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;
using Puj.Analytics.Services;

namespace Puj.Analytics.Consumers;

public class AssessmentSubmittedConsumer(
    AnalyticsDbContext db,
    CertificateService certificateService,
    ILogger<AssessmentSubmittedConsumer> logger)
    : IConsumer<AssessmentSubmittedMessage>
{
    public async Task Consume(ConsumeContext<AssessmentSubmittedMessage> context)
    {
        var msg      = context.Message;
        var courseId = Guid.Parse(msg.CourseId);
        var userId   = Guid.Parse(msg.UserId);
        logger.LogInformation("Processing ASSESSMENT_SUBMITTED: {SubmissionId}", msg.SubmissionId);

        // ── Platform-wide counters ────────────────────────────────────────────
        var stats = await db.PlatformStats.FirstOrDefaultAsync();
        if (stats == null) { stats = new PlatformStats(); db.PlatformStats.Add(stats); }
        stats.TotalSubmissions++;
        if (msg.MaxScore > 0)
        {
            stats.RawScoreSum    += msg.Score;
            stats.RawMaxScoreSum += msg.MaxScore;
        }
        if (msg.Passed) stats.PassCount++;
        stats.UpdatedAt = DateTime.UtcNow;

        // ── Per-course counters ───────────────────────────────────────────────
        var metric = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);
        if (metric == null)
        {
            metric = new CourseMetric { CourseId = courseId };
            db.CourseMetrics.Add(metric);
            stats.TotalCourses++;
        }
        metric.TotalSubmissions++;
        if (msg.MaxScore > 0)
        {
            metric.RawScoreSum    += msg.Score;
            metric.RawMaxScoreSum += msg.MaxScore;
        }
        if (msg.Passed) metric.PassCount++;
        metric.UpdatedAt = DateTime.UtcNow;

        await db.SaveChangesAsync();

        // ── Certificate ───────────────────────────────────────────────────────
        if (msg.AllAssessmentsPassed)
            await TryIssueCertificate(userId, courseId, metric.CourseTitle);
    }

    private async Task TryIssueCertificate(Guid studentId, Guid courseId, string courseTitle)
    {
        try
        {
            var alreadyCertified = await db.Certificates
                .AnyAsync(c => c.StudentId == studentId && c.CourseId == courseId);
            if (alreadyCertified) return;

            var cache       = await db.StudentNameCaches.FirstOrDefaultAsync(c => c.UserId == studentId);
            string name     = cache?.StudentName ?? "Estudiante";

            await certificateService.IssueCertificate(
                studentId, courseId, courseTitle, name, "PUJ Learning Platform");
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Error al emitir certificado student={S} course={C}", studentId, courseId);
        }
    }
}
