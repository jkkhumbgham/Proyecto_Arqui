using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

/// <summary>
/// Endpoints REST para consultar métricas agregadas de cursos individuales.
///
/// <para>
/// Expone un endpoint bajo <c>/api/v1/analytics/courses</c> destinado a
/// instructores y administradores que necesitan ver el rendimiento detallado
/// de un curso específico.
/// </para>
/// </summary>
public static class CourseAnalyticsEndpoints
{
    /// <summary>
    /// Registra los endpoints de analítica por curso en la aplicación web.
    /// </summary>
    /// <param name="app">La instancia de <see cref="WebApplication"/> en la que
    /// se registran los endpoints.</param>
    public static void MapCourseAnalyticsEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/v1/analytics/courses")
            .WithTags("Analítica de Cursos")
            .RequireAuthorization();

        // ── GET /api/v1/analytics/courses/{courseId}/summary ─────────────────
        group.MapGet("/{courseId}/summary", async (Guid courseId, AnalyticsDbContext db) =>
        {
            var metric = await db.CourseMetrics
                .FirstOrDefaultAsync(m => m.CourseId == courseId);

            if (metric == null)
                return Results.NotFound(new {
                    error   = "NOT_FOUND",
                    message = "Sin datos para este curso."
                });

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
