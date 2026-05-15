using MassTransit;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

public class UserRegisteredConsumer(AnalyticsDbContext db, ILogger<UserRegisteredConsumer> logger)
    : IConsumer<UserRegisteredMessage>
{
    public async Task Consume(ConsumeContext<UserRegisteredMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing USER_REGISTERED: {UserId}", msg.UserId);

        db.UserRecords.Add(new UserRecord {
            UserId       = Guid.Parse(msg.UserId),
            Email        = msg.Email,
            Role         = msg.Role,
            RegisteredAt = msg.OccurredAt
        });

        await db.SaveChangesAsync();
    }
}
