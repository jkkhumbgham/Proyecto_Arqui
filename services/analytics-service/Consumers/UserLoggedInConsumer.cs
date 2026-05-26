using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;

namespace Puj.Analytics.Consumers;

public class UserLoggedInConsumer(AnalyticsDbContext db, ILogger<UserLoggedInConsumer> logger)
    : IConsumer<UserLoggedInMessage>
{
    public async Task Consume(ConsumeContext<UserLoggedInMessage> context)
    {
        var msg    = context.Message;
        var userId = Guid.Parse(msg.UserId);
        logger.LogInformation("Processing USER_LOGGED_IN: {UserId}", msg.UserId);

        var cache = await db.StudentNameCaches.FirstOrDefaultAsync(c => c.UserId == userId);
        if (cache == null)
        {
            // Puede ocurrir si el registro llegó antes de que analytics arrancara
            cache = new Puj.Analytics.Models.StudentNameCache
            {
                UserId = userId,
                Email  = msg.Email ?? string.Empty,
                Role   = msg.Role  ?? string.Empty
            };
            db.StudentNameCaches.Add(cache);
        }

        cache.LastLoginAt = DateTime.UtcNow;
        cache.UpdatedAt   = DateTime.UtcNow;
        await db.SaveChangesAsync();
    }
}
