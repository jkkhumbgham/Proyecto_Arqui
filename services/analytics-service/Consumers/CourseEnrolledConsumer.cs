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
        var msg      = context.Message;
        var courseId = Guid.Parse(msg.CourseId);
        logger.LogInformation("Processing COURSE_ENROLLED: {UserId} -> {CourseId}", msg.UserId, msg.CourseId);

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
        metric.UpdatedAt  = DateTime.UtcNow;
        stats.UpdatedAt   = DateTime.UtcNow;

        await db.SaveChangesAsync();
    }
}
