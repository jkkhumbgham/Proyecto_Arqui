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

            var submissions = await db.SubmissionRecords
                .Where(s => s.CourseId == courseId)
                .ToListAsync();

            return Results.Ok(new {
                courseId         = metric.CourseId,
                courseTitle      = metric.CourseTitle,
                totalEnrollments = metric.TotalEnrollments,
                totalSubmissions = submissions.Count,
                averageScore     = metric.AverageScore,
                passRate         = metric.PassRate,
                avgDurationMin   = submissions.Count > 0
                    ? submissions.Average(s => s.DurationSeconds) / 60.0
                    : 0,
                updatedAt        = metric.UpdatedAt
            });
        })
        .WithSummary("Métricas de un curso (INSTRUCTOR/ADMIN)")
        .WithOpenApi();
    }
}
