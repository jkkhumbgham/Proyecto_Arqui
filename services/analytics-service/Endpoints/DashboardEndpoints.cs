using Microsoft.AspNetCore.Authorization;
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

            var totalUsers       = await db.UserRecords.CountAsync();
            var totalEnrollments = await db.EnrollmentRecords.CountAsync();
            var totalSubmissions = await db.SubmissionRecords.CountAsync();
            var passRate         = totalSubmissions == 0 ? 0m
                : await db.SubmissionRecords.AverageAsync(s => s.Passed ? 1m : 0m) * 100;

            return Results.Ok(new {
                totalUsers,
                totalEnrollments,
                totalSubmissions,
                overallPassRate = Math.Round(passRate, 2),
                generatedAt     = DateTime.UtcNow
            });
        })
        .WithSummary("Resumen institucional agregado (DIRECTOR/ADMIN)")
        .WithOpenApi();

        group.MapGet("/top-courses", async (AnalyticsDbContext db, int top = 5) =>
        {
            var courses = await db.CourseMetrics
                .OrderByDescending(c => c.TotalEnrollments)
                .Take(top)
                .Select(c => new {
                    c.CourseId, c.CourseTitle,
                    c.TotalEnrollments, c.TotalCompletions,
                    c.AverageScore, c.PassRate
                })
                .ToListAsync();

            return Results.Ok(courses);
        })
        .WithSummary("Top cursos por inscripción")
        .WithOpenApi();
    }
}
