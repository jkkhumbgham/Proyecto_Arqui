using MassTransit;
using Puj.Analytics.Messages;

namespace Puj.Analytics.Consumers;

// Reserved for future use — certificate issuance is handled in AssessmentSubmittedConsumer
public class LessonCompletedConsumer(ILogger<LessonCompletedConsumer> logger)
    : IConsumer<LessonCompletedMessage>
{
    public Task Consume(ConsumeContext<LessonCompletedMessage> context)
    {
        logger.LogDebug("LESSON_COMPLETED: user={UserId} course={CourseId}",
            context.Message.UserId, context.Message.CourseId);
        return Task.CompletedTask;
    }
}
