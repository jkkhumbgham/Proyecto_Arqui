using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

/// <summary>
/// Endpoints REST para consultar los snapshots mensuales de la plataforma.
///
/// <para>
/// Expone el historial de fotografías mensuales generadas por
/// <see cref="Puj.Analytics.Services.TrabajoResumenMensual"/>, ordenadas
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
        app.MapGet("/api/v1/analytics/monthly", async (ContextoBaseDatosAnaliticas baseDatos) =>
        {
            var resumenes = await baseDatos.ResumenesMensuales
                .OrderByDescending(s => s.Anio)
                .ThenByDescending(s => s.Mes)
                .ToListAsync();

            return Results.Ok(resumenes.Select(s => new {
                Year                    = s.Anio,
                Month                   = s.Mes,
                TotalUsers              = s.TotalUsuarios,
                TotalEnrollments        = s.TotalInscripciones,
                TotalSubmissions        = s.TotalEntregas,
                TotalCourses            = s.TotalCursos,
                AvgScore                = (double)s.PuntajePromedio,
                PassRate                = (double)s.TasaAprobacion,
                NewUsersThisMonth       = s.UsuariosNuevosEsteMes,
                NewEnrollmentsThisMonth = s.InscripcionesNuevasEsteMes,
                NewSubmissionsThisMonth = s.EntregasNuevasEsteMes,
                GeneratedAt             = s.GeneradoEn
            }));
        })
        .WithTags("Analytics")
        .WithName("GetMonthlySnapshots")
        .RequireAuthorization();
    }
}
