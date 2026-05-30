using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Models;

namespace Puj.Analytics.Services;

public class TrabajoResumenMensual(
    IServiceScopeFactory fabricaAlcance,
    ILogger<TrabajoResumenMensual> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await IntentarResumenMesAnteriorAsync(stoppingToken);

        while (!stoppingToken.IsCancellationRequested)
        {
            var demora = DemoraHastaPrimeroDelMes();
            logger.LogInformation("Próximo snapshot mensual en {Hours}h {Minutes}m.",
                (int)demora.TotalHours, demora.Minutes);

            await Task.Delay(demora, stoppingToken);
            await IntentarResumenMesAnteriorAsync(stoppingToken);
        }
    }

    private async Task IntentarResumenMesAnteriorAsync(CancellationToken ct)
    {
        var objetivo = new DateTime(DateTime.UtcNow.Year, DateTime.UtcNow.Month, 1, 0, 0, 0, DateTimeKind.Utc)
                           .AddMonths(-1);
        int anio  = objetivo.Year;
        int mes   = objetivo.Month;

        using var alcance = fabricaAlcance.CreateScope();
        var baseDatos = alcance.ServiceProvider.GetRequiredService<ContextoBaseDatosAnaliticas>();

        if (await baseDatos.ResumenesMensuales.AnyAsync(s => s.Anio == anio && s.Mes == mes, ct))
        {
            logger.LogDebug("Snapshot {Year}-{Month:D2} ya existe.", anio, mes);
            return;
        }

        var estadisticas = await baseDatos.EstadisticasPlataforma.FirstOrDefaultAsync(ct);
        if (estadisticas == null)
        {
            logger.LogWarning("platform_stats vacío — snapshot omitido.");
            return;
        }

        var resumenAnterior = await baseDatos.ResumenesMensuales
            .OrderByDescending(s => s.Anio).ThenByDescending(s => s.Mes)
            .FirstOrDefaultAsync(ct);

        long usuariosNuevos       = Math.Max(0, estadisticas.TotalUsuarios       - (resumenAnterior?.TotalUsuarios       ?? 0));
        long inscripcionesNuevas  = Math.Max(0, estadisticas.TotalInscripciones  - (resumenAnterior?.TotalInscripciones  ?? 0));
        long entregasNuevas       = Math.Max(0, estadisticas.TotalEntregas       - (resumenAnterior?.TotalEntregas        ?? 0));

        var resumen = new ResumenMensual
        {
            Anio                     = anio,
            Mes                      = mes,
            TotalUsuarios            = estadisticas.TotalUsuarios,
            TotalInscripciones       = estadisticas.TotalInscripciones,
            TotalEntregas            = estadisticas.TotalEntregas,
            TotalCursos              = estadisticas.TotalCursos,
            PuntajePromedio          = estadisticas.PuntajePromedio,
            TasaAprobacion           = estadisticas.TasaAprobacion,
            UsuariosNuevosEsteMes    = usuariosNuevos,
            InscripcionesNuevasEsteMes = inscripcionesNuevas,
            EntregasNuevasEsteMes    = entregasNuevas,
            GeneradoEn               = DateTime.UtcNow
        };

        baseDatos.ResumenesMensuales.Add(resumen);
        await baseDatos.SaveChangesAsync(ct);

        logger.LogInformation(
            "Snapshot mensual {Year}-{Month:D2} generado: {Users} usuarios, {Enroll} inscripciones, {Subs} entregas.",
            anio, mes, estadisticas.TotalUsuarios, estadisticas.TotalInscripciones, estadisticas.TotalEntregas);
    }

    private static TimeSpan DemoraHastaPrimeroDelMes()
    {
        var ahora     = DateTime.UtcNow;
        var siguiente = new DateTime(ahora.Year, ahora.Month, 1, 0, 5, 0, DateTimeKind.Utc).AddMonths(1);
        var diferencia = siguiente - ahora;
        return diferencia > TimeSpan.Zero ? diferencia : TimeSpan.FromHours(24);
    }
}
