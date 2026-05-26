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

        // Usuarios que no han iniciado sesión en los últimos N días
        // Incluye quienes nunca hicieron login (LastLoginAt == null)
        group.MapGet("/inactive-users", async (AnalyticsDbContext db, HttpContext ctx,
                                               int days = 30, int size = 50) =>
        {
            var role = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                    ?? ctx.User.FindFirst("role")?.Value ?? "";
            if (role != "DIRECTOR" && role != "ADMIN")
                return Results.Forbid();

            var threshold = DateTime.UtcNow.AddDays(-days);

            // Nulls primero (nunca entraron), luego más antiguos.
            // EF Core no soporta NULLS FIRST directamente; usamos columna auxiliar.
            var inactive = await db.StudentNameCaches
                .Where(c => c.LastLoginAt == null || c.LastLoginAt < threshold)
                .OrderBy(c => c.LastLoginAt == null ? 0 : 1)   // null=0 → primero
                .ThenBy(c => c.LastLoginAt)
                .Take(size)
                .Select(c => new {
                    userId      = c.UserId,
                    email       = c.Email,
                    studentName = c.StudentName,
                    role        = c.Role,
                    lastLoginAt = c.LastLoginAt == null
                                    ? "Nunca"
                                    : c.LastLoginAt.Value.ToString("yyyy-MM-dd HH:mm")
                })
                .ToListAsync();

            return Results.Ok(inactive);
        })
        .WithSummary("Usuarios sin actividad en los últimos N días (DIRECTOR/ADMIN)");
    }
}
