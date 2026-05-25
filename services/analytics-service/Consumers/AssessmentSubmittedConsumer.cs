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
        var msg      = context.Message;
        var courseId = Guid.Parse(msg.CourseId);
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
    }
}
