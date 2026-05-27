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

        // Retry-safe: catch duplicate-key on course_metrics and re-read on conflict.
        // Multiple concurrent enrollments in the same course can race on the INSERT.
        // Bug fijo: condición era invertida — retries >= 0 significa "aún hay intentos",
        //           retries < 0 significa "agotados, usar fallback".
        int retries = 2;
        while (retries-- >= 0)
        {
            try
            {
                // Reload stats inside the retry loop so the DbContext is clean after a conflict.
                db.ChangeTracker.Clear();

                // ── platform_stats ────────────────────────────────────────────────
                // Usamos UPDATE atómico para evitar lost-update cuando múltiples mensajes
                // llegan concurrentemente. Si no hay fila todavía, insertamos una.
                int updated = await db.Database.ExecuteSqlRawAsync(
                    "UPDATE analytics.platform_stats SET total_enrollments = total_enrollments + 1, updated_at = NOW()");
                if (updated == 0)
                {
                    // Primera vez: insertar fila inicial con este enrollment
                    db.PlatformStats.Add(new PlatformStats { TotalEnrollments = 1, UpdatedAt = DateTime.UtcNow });
                }

                // ── course_metrics ────────────────────────────────────────────────
                var metric = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);
                if (metric == null)
                {
                    metric = new CourseMetric { CourseId = courseId, CourseTitle = msg.CourseTitle };
                    db.CourseMetrics.Add(metric);
                    // Incremento atómico de TotalCourses para el nuevo curso
                    await db.Database.ExecuteSqlRawAsync(
                        "UPDATE analytics.platform_stats SET total_courses = total_courses + 1, updated_at = NOW()");
                }
                metric.TotalEnrollments++;
                metric.UpdatedAt = DateTime.UtcNow;

                await db.SaveChangesAsync();
                return;   // success
            }
            catch (DbUpdateException ex) when (ex.InnerException?.Message.Contains("23505") == true
                                               || ex.InnerException?.Message.Contains("duplicate key") == true)
            {
                if (retries >= 0)
                {
                    // Aún quedan intentos — reintenta (el while loop recarga el DbContext)
                    logger.LogWarning("COURSE_ENROLLED — duplicate key en course_metrics para {CourseId}, reintentando... ({Retries} restantes)", courseId, retries);
                }
                else
                {
                    // Agotados los reintentos: la fila ya existe (la ganó otro consumer concurrente).
                    // Incrementamos solo el conteo de enrollment; TotalCourses ya fue contado por el ganador.
                    db.ChangeTracker.Clear();
                    await db.Database.ExecuteSqlRawAsync(
                        "UPDATE analytics.platform_stats SET total_enrollments = total_enrollments + 1, updated_at = NOW()");
                    var metric2 = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);
                    if (metric2 != null)
                    {
                        metric2.TotalEnrollments++;
                        metric2.UpdatedAt = DateTime.UtcNow;
                        await db.SaveChangesAsync();
                    }
                    logger.LogInformation("COURSE_ENROLLED — conflicto resuelto para {CourseId}: enrollment contabilizado", courseId);
                    return;
                }
            }
        }
    }
}
