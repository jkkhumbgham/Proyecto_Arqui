using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

/// <summary>
/// Consumer de MassTransit que procesa el evento <c>COURSE_ENROLLED</c>.
///
/// <para>
/// Es el único responsable de crear registros <see cref="MetricaCurso"/>
/// e incrementar <c>total_courses</c> en <see cref="EstadisticasPlataforma"/>.
/// Usa una transacción explícita para garantizar que tanto el INSERT/UPDATE
/// del metric como el UPDATE de platform_stats sean atómicos; si ocurre
/// una violación de clave única (race condition entre consumers del mismo
/// curso), la transacción se revierte limpiamente y MassTransit reintenta.
/// </para>
/// </summary>
/// <param name="baseDatos">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class ConsumidorMatriculaCurso(
    ContextoBaseDatosAnaliticas baseDatos,
    ILogger<ConsumidorMatriculaCurso> logger)
    : IConsumer<CourseEnrolledMessage>
{
    /// <summary>
    /// Procesa un mensaje <see cref="CourseEnrolledMessage"/> recibido desde
    /// RabbitMQ, crea o actualiza el <see cref="MetricaCurso"/> del curso e
    /// incrementa los contadores de inscripciones (y cursos, si es nuevo) en
    /// <see cref="EstadisticasPlataforma"/>.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea que representa la operación asíncrona.</returns>
    public async Task Consume(ConsumeContext<CourseEnrolledMessage> context)
    {
        var mensaje = context.Message;
        logger.LogInformation(
            "Processing COURSE_ENROLLED: UserId={UserId} CourseId={CourseId} " +
            "CourseTitle={CourseTitle}",
            mensaje.UserId, mensaje.CourseId, mensaje.CourseTitle);

        if (string.IsNullOrWhiteSpace(mensaje.CourseId)
            || !Guid.TryParse(mensaje.CourseId, out var idCurso))
        {
            logger.LogWarning(
                "COURSE_ENROLLED descartado — CourseId inválido o nulo: '{CourseId}'",
                mensaje.CourseId);
            return;
        }

        // ── Transacción explícita ────────────────────────────────────────────
        // Envuelve el SaveChangesAsync (EF Core) y los UPDATE de platform_stats
        // (raw SQL) en la misma transacción PostgreSQL para eliminar el riesgo
        // de double-count en escenarios de race entre consumers del mismo curso.
        await using var transaccion = await baseDatos.Database.BeginTransactionAsync();

        // ── course_metrics ────────────────────────────────────────────────────
        var metrica = await baseDatos.MetricasCurso
            .FirstOrDefaultAsync(m => m.IdCurso == idCurso);
        bool esCursoNuevo = metrica == null;

        if (metrica == null)
        {
            metrica = new MetricaCurso
            {
                IdCurso    = idCurso,
                TituloCurso = mensaje.CourseTitle ?? string.Empty
            };
            baseDatos.MetricasCurso.Add(metrica);
        }

        metrica.TotalInscripciones++;
        metrica.ActualizadoEn = DateTime.UtcNow;

        // Si otro consumer ganó la race e insertó el mismo course_id,
        // SaveChangesAsync lanza DbUpdateException (duplicate key 23505).
        // La transacción aún no commitó nada → MassTransit reintenta limpio.
        await baseDatos.SaveChangesAsync();

        // ── platform_stats (raw SQL atómico, dentro de la misma transacción) ─
        // Se ejecuta DESPUÉS del SaveChanges exitoso para no duplicar contadores.
        int filasActualizadas = await baseDatos.Database.ExecuteSqlRawAsync(
            esCursoNuevo
                ? "UPDATE analytics.platform_stats " +
                  "SET total_enrollments = total_enrollments + 1, " +
                  "    total_courses = total_courses + 1, " +
                  "    updated_at = NOW()"
                : "UPDATE analytics.platform_stats " +
                  "SET total_enrollments = total_enrollments + 1, " +
                  "    updated_at = NOW()");

        if (filasActualizadas == 0)
        {
            // Primer evento: todavía no hay fila en platform_stats.
            // Ocurre si analytics_db fue truncada y este enrollment llega
            // antes que un USER_REGISTERED.
            var idNuevasEstadisticas = Guid.NewGuid();
            await baseDatos.Database.ExecuteSqlRawAsync(
                "INSERT INTO analytics.platform_stats " +
                "(id, total_enrollments, total_courses, updated_at) " +
                "VALUES ({0}, 1, {1}, NOW())",
                idNuevasEstadisticas, esCursoNuevo ? 1 : 0);
        }

        await transaccion.CommitAsync();
        logger.LogInformation(
            "COURSE_ENROLLED procesado: CourseId={CourseId} NuevoCurso={IsNew}",
            idCurso, esCursoNuevo);
    }
}
