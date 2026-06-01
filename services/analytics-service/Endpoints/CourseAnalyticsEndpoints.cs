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
        var grupo = app.MapGroup("/api/v1/analytics/courses")
            .WithTags("Analítica de Cursos")
            .RequireAuthorization();

        // ── GET /api/v1/analytics/courses/{courseId}/summary ─────────────────
        grupo.MapGet("/{courseId}/summary", async (Guid courseId, ContextoBaseDatosAnaliticas baseDatos) =>
        {
            var metrica = await baseDatos.MetricasCurso
                .FirstOrDefaultAsync(m => m.IdCurso == courseId);

            if (metrica == null)
                return Results.NotFound(new {
                    error   = "NOT_FOUND",
                    message = "Sin datos para este curso."
                });

            return Results.Ok(new {
                courseId         = metrica.IdCurso,
                courseTitle      = metrica.TituloCurso,
                totalEnrollments = metrica.TotalInscripciones,
                totalSubmissions = metrica.TotalEntregas,
                averageScore     = metrica.PuntajePromedio,
                passRate         = metrica.TasaAprobacion,
                updatedAt        = metrica.ActualizadoEn
            });
        })
        .WithSummary("Métricas de un curso (INSTRUCTOR/ADMIN)");
    }
}
