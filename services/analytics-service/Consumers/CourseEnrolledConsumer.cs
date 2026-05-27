using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

public class CourseEnrolledConsumer(AnalyticsDbContext db, ILogger<CourseEnrolledConsumer> logger)
    : IConsumer<CourseEnrolledMessage>
{
    public async Task Consume(ConsumeContext<CourseEnrolledMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing COURSE_ENROLLED: UserId={UserId} CourseId={CourseId} CourseTitle={CourseTitle}",
            msg.UserId, msg.CourseId, msg.CourseTitle);

        if (string.IsNullOrWhiteSpace(msg.CourseId) || !Guid.TryParse(msg.CourseId, out var courseId))
        {
            logger.LogWarning("COURSE_ENROLLED descartado — CourseId inválido o nulo: '{CourseId}'", msg.CourseId);
            return;
        }

        // Retry-safe: catch duplicate-key on course_metrics and re-read on conflict.
        // Multiple concurrent enrollments in the same course can race on the INSERT.
        int retries = 2;
        while (retries-- >= 0)
        {
            try
            {
                // Reload stats inside the retry loop so the DbContext is clean after a conflict.
                db.ChangeTracker.Clear();

                var stats = await db.PlatformStats.FirstOrDefaultAsync();
                if (stats == null) { stats = new PlatformStats(); db.PlatformStats.Add(stats); }
                stats.TotalEnrollments++;

                var metric = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);
                if (metric == null)
                {
                    metric = new CourseMetric { CourseId = courseId, CourseTitle = msg.CourseTitle };
                    db.CourseMetrics.Add(metric);
                    stats.TotalCourses++;
                }
                metric.TotalEnrollments++;
                metric.UpdatedAt = DateTime.UtcNow;
                stats.UpdatedAt  = DateTime.UtcNow;

                await db.SaveChangesAsync();
                return;   // success
            }
            catch (DbUpdateException ex) when (ex.InnerException?.Message.Contains("23505") == true
                                               || ex.InnerException?.Message.Contains("duplicate key") == true)
            {
                if (retries < 0)
                {
                    logger.LogWarning("COURSE_ENROLLED — duplicate key en course_metrics para {CourseId}, reintentando...", courseId);
                }
                else
                {
                    // Exhausted retries: the CourseMetric row already exists (created by a concurrent message).
                    // Increment enrollment count only — the TotalCourses was already counted by the winner.
                    db.ChangeTracker.Clear();
                    var stats2 = await db.PlatformStats.FirstOrDefaultAsync();
                    if (stats2 != null)
                    {
                        stats2.TotalEnrollments++;
                        stats2.UpdatedAt = DateTime.UtcNow;
                    }
                    var metric2 = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);
                    if (metric2 != null)
                    {
                        metric2.TotalEnrollments++;
                        metric2.UpdatedAt = DateTime.UtcNow;
                    }
                    await db.SaveChangesAsync();
                    logger.LogInformation("COURSE_ENROLLED — conflicto resuelto para {CourseId}: enrollment contabilizado", courseId);
                    return;
                }
            }
        }
    }
}
