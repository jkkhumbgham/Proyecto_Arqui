using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

/// <summary>
/// Endpoints REST del dashboard institucional de analítica.
///
/// <para>
/// Agrupa tres endpoints de solo lectura bajo <c>/api/v1/analytics/dashboard</c>:
/// resumen global de plataforma, ranking de cursos por inscripciones y
/// listado de usuarios inactivos. El acceso al resumen y a usuarios inactivos
/// está restringido a los roles <c>DIRECTOR</c> y <c>ADMIN</c>.
/// </para>
/// </summary>
public static class DashboardEndpoints
{
    /// <summary>
    /// Registra los endpoints del dashboard en la aplicación web.
    /// </summary>
    /// <param name="app">La instancia de <see cref="WebApplication"/> en la que
    /// se registran los endpoints.</param>
    public static void MapDashboardEndpoints(this WebApplication app)
    {
        var grupo = app.MapGroup("/api/v1/analytics/dashboard")
            .WithTags("Dashboard")
            .RequireAuthorization();

        // ── GET /api/v1/analytics/dashboard/summary ───────────────────────────
        grupo.MapGet("/summary", async (ContextoBaseDatosAnaliticas baseDatos, HttpContext ctx) =>
        {
            var rol = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                    ?? ctx.User.FindFirst("role")?.Value ?? "";

            if (rol != "DIRECTOR" && rol != "ADMIN")
                return Results.Forbid();

            var estadisticas = await baseDatos.EstadisticasPlataforma.FirstOrDefaultAsync();
            if (estadisticas == null)
                return Results.Ok(new {
                    totalUsers       = 0L,
                    totalEnrollments = 0L,
                    totalSubmissions = 0L,
                    totalCourses     = 0L,
                    averageScore     = 0m,
                    passRate         = 0m,
                    overallPassRate  = 0m,
                    generatedAt      = DateTime.UtcNow
                });

            return Results.Ok(new {
                totalUsers       = estadisticas.TotalUsuarios,
                totalEnrollments = estadisticas.TotalInscripciones,
                totalSubmissions = estadisticas.TotalEntregas,
                totalCourses     = estadisticas.TotalCursos,
                averageScore     = estadisticas.PuntajePromedio,
                passRate         = estadisticas.TasaAprobacion,
                overallPassRate  = estadisticas.TasaAprobacion,
                generatedAt      = estadisticas.ActualizadoEn
            });
        })
        .WithSummary("Resumen institucional en tiempo real (DIRECTOR/ADMIN)");

        // ── GET /api/v1/analytics/dashboard/top-courses ───────────────────────
        grupo.MapGet("/top-courses", async (ContextoBaseDatosAnaliticas baseDatos, int top = 5) =>
        {
            var cursos = await baseDatos.MetricasCurso
                .OrderByDescending(c => c.TotalInscripciones)
                .Take(top)
                .ToListAsync();

            return Results.Ok(cursos.Select(c => new {
                courseId         = c.IdCurso,
                courseTitle      = c.TituloCurso,
                totalEnrollments = c.TotalInscripciones,
                averageScore     = c.PuntajePromedio,
                passRate         = c.TasaAprobacion
            }));
        })
        .WithSummary("Top cursos por inscripción");

        // ── GET /api/v1/analytics/dashboard/inactive-users ───────────────────
        // Usuarios que no han iniciado sesión en los últimos N días.
        // Incluye quienes nunca hicieron login (UltimoAccesoEn == null).
        grupo.MapGet("/inactive-users", async (
            ContextoBaseDatosAnaliticas baseDatos,
            HttpContext ctx,
            int days = 30,
            int size = 50) =>
        {
            var rol = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                    ?? ctx.User.FindFirst("role")?.Value ?? "";

            if (rol != "DIRECTOR" && rol != "ADMIN")
                return Results.Forbid();

            var umbral = DateTime.UtcNow.AddDays(-days);

            // Nulls primero (nunca iniciaron sesión), luego más antiguos.
            // EF Core no soporta NULLS FIRST directamente; se usa columna auxiliar.
            var inactivos = await baseDatos.CachesNombreEstudiante
                .Where(c => c.UltimoAccesoEn == null || c.UltimoAccesoEn < umbral)
                .OrderBy(c => c.UltimoAccesoEn == null ? 0 : 1)
                .ThenBy(c => c.UltimoAccesoEn)
                .Take(size)
                .Select(c => new {
                    userId      = c.IdUsuario,
                    email       = c.Correo,
                    studentName = c.NombreEstudiante,
                    role        = c.Rol,
                    lastLoginAt = c.UltimoAccesoEn == null
                        ? "Nunca"
                        : c.UltimoAccesoEn.Value.ToString("yyyy-MM-dd HH:mm")
                })
                .ToListAsync();

            return Results.Ok(inactivos);
        })
        .WithSummary("Usuarios sin actividad en los últimos N días (DIRECTOR/ADMIN)");
    }
}
