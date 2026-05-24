using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

public static class DashboardEndpoints
{
    public static void MapDashboardEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/v1/analytics/dashboard")
            .WithTags("Dashboard")
            .RequireAuthorization();

        group.MapGet("/summary", async (AnalyticsDbContext db, HttpContext ctx) =>
        {
            var role = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                    ?? ctx.User.FindFirst("role")?.Value ?? "";

            if (role != "DIRECTOR" && role != "ADMIN")
                return Results.Forbid();

            var stats = await db.PlatformStats.FirstOrDefaultAsync();
            if (stats == null)
                return Results.Ok(new {
                    totalUsers=0L, totalEnrollments=0L, totalSubmissions=0L,
                    totalCourses=0L, averageScore=0m, passRate=0m, overallPassRate=0m,
                    generatedAt=DateTime.UtcNow
                });

            return Results.Ok(new {
                totalUsers       = stats.TotalUsers,
                totalEnrollments = stats.TotalEnrollments,
                totalSubmissions = stats.TotalSubmissions,
                totalCourses     = stats.TotalCourses,
                averageScore     = stats.AvgScore,
                passRate         = stats.PassRate,
                overallPassRate  = stats.PassRate,
                generatedAt      = stats.UpdatedAt
            });
        })
        .WithSummary("Resumen institucional en tiempo real (DIRECTOR/ADMIN)");

        group.MapGet("/top-courses", async (AnalyticsDbContext db, int top = 5) =>
        {
            var courses = await db.CourseMetrics
                .OrderByDescending(c => c.TotalEnrollments)
                .Take(top)
                .ToListAsync();

            return Results.Ok(courses.Select(c => new {
                courseId         = c.CourseId,
                courseTitle      = c.CourseTitle,
                totalEnrollments = c.TotalEnrollments,
                averageScore     = c.AverageScore,
                passRate         = c.PassRate
            }));
        })
        .WithSummary("Top cursos por inscripción");
    }
}
