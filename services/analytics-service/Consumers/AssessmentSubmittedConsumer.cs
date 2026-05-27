using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

public class AssessmentSubmittedConsumer(
    AnalyticsDbContext db,
    ILogger<AssessmentSubmittedConsumer> logger)
    : IConsumer<AssessmentSubmittedMessage>
{
    public async Task Consume(ConsumeContext<AssessmentSubmittedMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing ASSESSMENT_SUBMITTED: SubmissionId={SubmissionId} CourseId={CourseId} Score={Score}/{MaxScore} Passed={Passed}",
            msg.SubmissionId, msg.CourseId, msg.Score, msg.MaxScore, msg.Passed);

        if (string.IsNullOrWhiteSpace(msg.CourseId) || !Guid.TryParse(msg.CourseId, out var courseId))
        {
            logger.LogWarning("ASSESSMENT_SUBMITTED descartado — CourseId inválido o nulo: '{CourseId}'", msg.CourseId);
            return;
        }

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
    }
}
