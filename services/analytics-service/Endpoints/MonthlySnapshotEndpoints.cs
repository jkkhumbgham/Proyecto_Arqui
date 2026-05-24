using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

public static class MonthlySnapshotEndpoints
{
    public static void MapMonthlySnapshotEndpoints(this WebApplication app)
    {
        app.MapGet("/api/v1/analytics/monthly", async (AnalyticsDbContext db) =>
        {
            var snapshots = await db.MonthlySnapshots
                .OrderByDescending(s => s.Year)
                .ThenByDescending(s => s.Month)
                .ToListAsync();

            return Results.Ok(snapshots.Select(s => new {
                s.Year,
                s.Month,
                s.TotalUsers,
                s.TotalEnrollments,
                s.TotalSubmissions,
                s.TotalCourses,
                AvgScore  = (double)s.AvgScore,
                PassRate  = (double)s.PassRate,
                s.NewUsersThisMonth,
                s.NewEnrollmentsThisMonth,
                s.NewSubmissionsThisMonth,
                s.GeneratedAt
            }));
        })
        .WithTags("Analytics")
        .WithName("GetMonthlySnapshots")
        .RequireAuthorization();
    }
}
