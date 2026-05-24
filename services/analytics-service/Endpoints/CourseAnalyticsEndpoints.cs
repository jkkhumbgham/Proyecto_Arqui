using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

public static class CourseAnalyticsEndpoints
{
    public static void MapCourseAnalyticsEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/v1/analytics/courses")
            .WithTags("Analítica de Cursos")
            .RequireAuthorization();

        group.MapGet("/{courseId}/summary", async (Guid courseId, AnalyticsDbContext db) =>
        {
            var metric = await db.CourseMetrics
                .FirstOrDefaultAsync(m => m.CourseId == courseId);

            if (metric == null)
                return Results.NotFound(new { error = "NOT_FOUND", message = "Sin datos para este curso." });

            return Results.Ok(new {
                courseId         = metric.CourseId,
                courseTitle      = metric.CourseTitle,
                totalEnrollments = metric.TotalEnrollments,
                totalSubmissions = metric.TotalSubmissions,
                averageScore     = metric.AverageScore,
                passRate         = metric.PassRate,
                updatedAt        = metric.UpdatedAt
            });
        })
        .WithSummary("Métricas de un curso (INSTRUCTOR/ADMIN)");
    }
}
