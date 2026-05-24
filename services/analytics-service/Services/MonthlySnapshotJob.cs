using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Models;

namespace Puj.Analytics.Services;

public class MonthlySnapshotJob(
    IServiceScopeFactory scopeFactory,
    ILogger<MonthlySnapshotJob> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await TrySnapshotPreviousMonthAsync(stoppingToken);

        while (!stoppingToken.IsCancellationRequested)
        {
            var delay = DelayUntilNextFirstOfMonth();
            logger.LogInformation("Próximo snapshot mensual en {Hours}h {Minutes}m.",
                (int)delay.TotalHours, delay.Minutes);

            await Task.Delay(delay, stoppingToken);
            await TrySnapshotPreviousMonthAsync(stoppingToken);
        }
    }

    private async Task TrySnapshotPreviousMonthAsync(CancellationToken ct)
    {
        var target = new DateTime(DateTime.UtcNow.Year, DateTime.UtcNow.Month, 1, 0, 0, 0, DateTimeKind.Utc)
                         .AddMonths(-1);
        int year  = target.Year;
        int month = target.Month;

        using var scope = scopeFactory.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AnalyticsDbContext>();

        if (await db.MonthlySnapshots.AnyAsync(s => s.Year == year && s.Month == month, ct))
        {
            logger.LogDebug("Snapshot {Year}-{Month:D2} ya existe.", year, month);
            return;
        }

        var stats = await db.PlatformStats.FirstOrDefaultAsync(ct);
        if (stats == null)
        {
            logger.LogWarning("platform_stats vacío — snapshot omitido.");
            return;
        }

        var prevSnapshot = await db.MonthlySnapshots
            .OrderByDescending(s => s.Year).ThenByDescending(s => s.Month)
            .FirstOrDefaultAsync(ct);

        long newUsers       = Math.Max(0, stats.TotalUsers       - (prevSnapshot?.TotalUsers       ?? 0));
        long newEnrollments = Math.Max(0, stats.TotalEnrollments  - (prevSnapshot?.TotalEnrollments ?? 0));
        long newSubmissions = Math.Max(0, stats.TotalSubmissions   - (prevSnapshot?.TotalSubmissions ?? 0));

        var snapshot = new MonthlySnapshot
        {
            Year                    = year,
            Month                   = month,
            TotalUsers              = stats.TotalUsers,
            TotalEnrollments        = stats.TotalEnrollments,
            TotalSubmissions        = stats.TotalSubmissions,
            TotalCourses            = stats.TotalCourses,
            AvgScore                = stats.AvgScore,
            PassRate                = stats.PassRate,
            NewUsersThisMonth       = newUsers,
            NewEnrollmentsThisMonth = newEnrollments,
            NewSubmissionsThisMonth = newSubmissions,
            GeneratedAt             = DateTime.UtcNow
        };

        db.MonthlySnapshots.Add(snapshot);
        await db.SaveChangesAsync(ct);

        logger.LogInformation(
            "Snapshot mensual {Year}-{Month:D2} generado: {Users} usuarios, {Enroll} inscripciones, {Subs} entregas.",
            year, month, stats.TotalUsers, stats.TotalEnrollments, stats.TotalSubmissions);
    }

    private static TimeSpan DelayUntilNextFirstOfMonth()
    {
        var now  = DateTime.UtcNow;
        var next = new DateTime(now.Year, now.Month, 1, 0, 5, 0, DateTimeKind.Utc).AddMonths(1);
        var diff = next - now;
        return diff > TimeSpan.Zero ? diff : TimeSpan.FromHours(24);
    }
}
