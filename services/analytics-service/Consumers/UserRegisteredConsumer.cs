using MassTransit;
using Microsoft.EntityFrameworkCore;
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

        var stats = await db.PlatformStats.FirstOrDefaultAsync();
        if (stats == null) { stats = new PlatformStats(); db.PlatformStats.Add(stats); }
        stats.TotalUsers++;
        stats.UpdatedAt = DateTime.UtcNow;

        var userId = Guid.Parse(msg.UserId);
        var cache  = await db.StudentNameCaches.FirstOrDefaultAsync(c => c.UserId == userId);
        if (cache == null)
        {
            cache = new StudentNameCache { UserId = userId };
            db.StudentNameCaches.Add(cache);
        }
        cache.StudentName = $"{msg.FirstName} {msg.LastName}".Trim();
        cache.Email       = msg.Email ?? string.Empty;
        cache.Role        = msg.Role  ?? string.Empty;
        cache.UpdatedAt   = DateTime.UtcNow;

        await db.SaveChangesAsync();
    }
}
