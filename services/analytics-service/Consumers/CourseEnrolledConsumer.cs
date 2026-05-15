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
        logger.LogInformation("Processing COURSE_ENROLLED: {UserId} -> {CourseId}", msg.UserId, msg.CourseId);

        db.EnrollmentRecords.Add(new EnrollmentRecord {
            UserId      = Guid.Parse(msg.UserId),
            CourseId    = Guid.Parse(msg.CourseId),
            CourseTitle = msg.CourseTitle,
            OccurredAt  = msg.OccurredAt
        });

        var metric = await db.CourseMetrics
            .FirstOrDefaultAsync(m => m.CourseId == Guid.Parse(msg.CourseId));

        if (metric == null) {
            metric = new CourseMetric {
                CourseId    = Guid.Parse(msg.CourseId),
                CourseTitle = msg.CourseTitle
            };
            db.CourseMetrics.Add(metric);
        }

        metric.TotalEnrollments++;
        metric.UpdatedAt = DateTime.UtcNow;

        await db.SaveChangesAsync();
    }
}
