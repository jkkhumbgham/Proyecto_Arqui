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
/// Es el único responsable de crear registros <see cref="CourseMetric"/>
/// e incrementar <c>total_courses</c> en <see cref="PlatformStats"/>.
/// Usa una transacción explícita para garantizar que tanto el INSERT/UPDATE
/// del metric como el UPDATE de platform_stats sean atómicos; si ocurre
/// una violación de clave única (race condition entre consumers del mismo
/// curso), la transacción se revierte limpiamente y MassTransit reintenta.
/// </para>
/// </summary>
/// <param name="db">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class CourseEnrolledConsumer(
    AnalyticsDbContext db,
    ILogger<CourseEnrolledConsumer> logger)
    : IConsumer<CourseEnrolledMessage>
{
    /// <summary>
    /// Procesa un mensaje <see cref="CourseEnrolledMessage"/> recibido desde
    /// RabbitMQ, crea o actualiza el <see cref="CourseMetric"/> del curso e
    /// incrementa los contadores de inscripciones (y cursos, si es nuevo) en
    /// <see cref="PlatformStats"/>.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea que representa la operación asíncrona.</returns>
    public async Task Consume(ConsumeContext<CourseEnrolledMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation(
            "Processing COURSE_ENROLLED: UserId={UserId} CourseId={CourseId} " +
            "CourseTitle={CourseTitle}",
            msg.UserId, msg.CourseId, msg.CourseTitle);

        if (string.IsNullOrWhiteSpace(msg.CourseId)
            || !Guid.TryParse(msg.CourseId, out var courseId))
        {
            logger.LogWarning(
                "COURSE_ENROLLED descartado — CourseId inválido o nulo: '{CourseId}'",
                msg.CourseId);
            return;
        }

        // ── Transacción explícita ────────────────────────────────────────────
        // Envuelve el SaveChangesAsync (EF Core) y los UPDATE de platform_stats
        // (raw SQL) en la misma transacción PostgreSQL para eliminar el riesgo
        // de double-count en escenarios de race entre consumers del mismo curso.
        await using var tx = await db.Database.BeginTransactionAsync();

        // ── course_metrics ────────────────────────────────────────────────────
        var metric = await db.CourseMetrics
            .FirstOrDefaultAsync(m => m.CourseId == courseId);
        bool isNewCourse = metric == null;

        if (metric == null)
        {
            metric = new CourseMetric
            {
                CourseId    = courseId,
                CourseTitle = msg.CourseTitle ?? string.Empty
            };
            db.CourseMetrics.Add(metric);
        }

        metric.TotalEnrollments++;
        metric.UpdatedAt = DateTime.UtcNow;

        // Si otro consumer ganó la race e insertó el mismo course_id,
        // SaveChangesAsync lanza DbUpdateException (duplicate key 23505).
        // La transacción aún no commitó nada → MassTransit reintenta limpio.
        await db.SaveChangesAsync();

        // ── platform_stats (raw SQL atómico, dentro de la misma transacción) ─
        // Se ejecuta DESPUÉS del SaveChanges exitoso para no duplicar contadores.
        int updated = await db.Database.ExecuteSqlRawAsync(
            isNewCourse
                ? "UPDATE analytics.platform_stats " +
                  "SET total_enrollments = total_enrollments + 1, " +
                  "    total_courses = total_courses + 1, " +
                  "    updated_at = NOW()"
                : "UPDATE analytics.platform_stats " +
                  "SET total_enrollments = total_enrollments + 1, " +
                  "    updated_at = NOW()");

        if (updated == 0)
        {
            // Primer evento: todavía no hay fila en platform_stats.
            // Ocurre si analytics_db fue truncada y este enrollment llega
            // antes que un USER_REGISTERED.
            var newStatsId = Guid.NewGuid();
            await db.Database.ExecuteSqlRawAsync(
                "INSERT INTO analytics.platform_stats " +
                "(id, total_enrollments, total_courses, updated_at) " +
                "VALUES ({0}, 1, {1}, NOW())",
                newStatsId, isNewCourse ? 1 : 0);
        }

        await tx.CommitAsync();
        logger.LogInformation(
            "COURSE_ENROLLED procesado: CourseId={CourseId} NuevoCurso={IsNew}",
            courseId, isNewCourse);
    }
}
