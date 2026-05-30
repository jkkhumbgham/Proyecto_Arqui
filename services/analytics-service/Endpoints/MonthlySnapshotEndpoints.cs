using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

/// <summary>
/// Endpoints REST para consultar los snapshots mensuales de la plataforma.
///
/// <para>
/// Expone el historial de fotografías mensuales generadas por
/// <see cref="Puj.Analytics.Services.MonthlySnapshotJob"/>, ordenadas
/// de más reciente a más antigua. Requiere autenticación JWT.
/// </para>
/// </summary>
public static class MonthlySnapshotEndpoints
{
    /// <summary>
    /// Registra el endpoint de snapshots mensuales en la aplicación web.
    /// </summary>
    /// <param name="app">La instancia de <see cref="WebApplication"/> en la que
    /// se registra el endpoint.</param>
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
                AvgScore                = (double)s.AvgScore,
                PassRate                = (double)s.PassRate,
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
