using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

public class AssessmentSubmittedConsumer(AnalyticsDbContext db, ILogger<AssessmentSubmittedConsumer> logger)
    : IConsumer<AssessmentSubmittedMessage>
{
    public async Task Consume(ConsumeContext<AssessmentSubmittedMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing ASSESSMENT_SUBMITTED: {SubmissionId}", msg.SubmissionId);

        var record = new SubmissionRecord {
            UserId          = Guid.Parse(msg.UserId),
            AssessmentId    = Guid.Parse(msg.AssessmentId),
            CourseId        = Guid.Parse(msg.CourseId),
            LessonId        = msg.LessonId != null ? Guid.Parse(msg.LessonId) : null,
            Score           = msg.Score,
            MaxScore        = msg.MaxScore,
            Passed          = msg.Passed,
            DurationSeconds = msg.DurationSeconds,
            OccurredAt      = msg.OccurredAt
        };

        db.SubmissionRecords.Add(record);

        // Actualizar métricas agregadas del curso
        var metric = await db.CourseMetrics
            .FirstOrDefaultAsync(m => m.CourseId == record.CourseId);

        if (metric != null)
        {
            var courseSubmissions = await db.SubmissionRecords
                .Where(s => s.CourseId == record.CourseId)
                .ToListAsync();
            courseSubmissions.Add(record);

            metric.AverageScore = courseSubmissions.Average(s => s.Score);
            metric.PassRate     = (decimal) courseSubmissions.Count(s => s.Passed) / courseSubmissions.Count * 100;
            metric.UpdatedAt    = DateTime.UtcNow;
        }

        await db.SaveChangesAsync();
    }
}
