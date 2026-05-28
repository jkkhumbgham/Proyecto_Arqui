using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

public class CourseEnrolledConsumer(AnalyticsDbContext db, ILogger<CourseEnrolledConsumer> logger)
    : IConsumer<CourseEnrolledMessage>
{
    public async Task Consume(ConsumeContext<CourseEnrolledMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing COURSE_ENROLLED: UserId={UserId} CourseId={CourseId} CourseTitle={CourseTitle}",
            msg.UserId, msg.CourseId, msg.CourseTitle);

        if (string.IsNullOrWhiteSpace(msg.CourseId) || !Guid.TryParse(msg.CourseId, out var courseId))
        {
            logger.LogWarning("COURSE_ENROLLED descartado — CourseId inválido o nulo: '{CourseId}'", msg.CourseId);
            return;
        }

        // ── Transacción explícita ────────────────────────────────────────────
        // Envuelve tanto el SaveChangesAsync (EF Core) como los UPDATE de
        // platform_stats (raw SQL) en la MISMA transacción PostgreSQL.
        //
        // Esto elimina el double-count del código anterior: si SaveChangesAsync
        // lanza una excepción de clave duplicada en course_metrics (race entre
        // dos consumers del mismo curso), la transacción entera se revierte —
        // incluyendo el UPDATE de total_enrollments que aún no ocurrió.
        // MassTransit reintentará el mensaje y en el reintento el metric ya
        // existe, así que solo se incrementa total_enrollments (sin total_courses).
        await using var tx = await db.Database.BeginTransactionAsync();

        // ── course_metrics ────────────────────────────────────────────────────
        var metric = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);
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

        // Si otro consumer ganó la race y ya insertó el mismo course_id,
        // SaveChangesAsync lanza DbUpdateException (duplicate key 23505).
        // La transacción aún no commitó nada → MassTransit reintenta limpio.
        await db.SaveChangesAsync();

        // ── platform_stats (raw SQL atómico, dentro de la misma transacción) ─
        // El UPDATE usa incremento atómico para evitar lost-update concurrente.
        // Se ejecuta DESPUÉS del SaveChanges exitoso → nunca se duplica.
        int updated = await db.Database.ExecuteSqlRawAsync(
            isNewCourse
                ? "UPDATE analytics.platform_stats SET total_enrollments = total_enrollments + 1, total_courses = total_courses + 1, updated_at = NOW()"
                : "UPDATE analytics.platform_stats SET total_enrollments = total_enrollments + 1, updated_at = NOW()");

        if (updated == 0)
        {
            // Primer evento: todavía no hay fila en platform_stats
            // (ocurre si analytics_db fue truncada y este enrollment llega antes que un USER_REGISTERED)
            var newStatsId = Guid.NewGuid();
            await db.Database.ExecuteSqlRawAsync(
                "INSERT INTO analytics.platform_stats (id, total_enrollments, total_courses, updated_at) VALUES ({0}, 1, {1}, NOW())",
                newStatsId, isNewCourse ? 1 : 0);
        }

        await tx.CommitAsync();
        logger.LogInformation("COURSE_ENROLLED procesado: CourseId={CourseId} NuevoCurso={IsNew}", courseId, isNewCourse);
    }
}
