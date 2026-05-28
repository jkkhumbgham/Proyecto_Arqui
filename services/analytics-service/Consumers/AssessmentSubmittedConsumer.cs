using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

public class AssessmentSubmittedConsumer(
    AnalyticsDbContext db,
    ILogger<AssessmentSubmittedConsumer> logger)
    : IConsumer<AssessmentSubmittedMessage>
{
    public async Task Consume(ConsumeContext<AssessmentSubmittedMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation("Processing ASSESSMENT_SUBMITTED: SubmissionId={SubmissionId} CourseId={CourseId} Score={Score}/{MaxScore} Passed={Passed}",
            msg.SubmissionId, msg.CourseId, msg.Score, msg.MaxScore, msg.Passed);

        if (string.IsNullOrWhiteSpace(msg.CourseId) || !Guid.TryParse(msg.CourseId, out var courseId))
        {
            logger.LogWarning("ASSESSMENT_SUBMITTED descartado — CourseId inválido o nulo: '{CourseId}'", msg.CourseId);
            return;
        }

        decimal scoreToAdd    = msg.MaxScore > 0 ? msg.Score    : 0m;
        decimal maxScoreToAdd = msg.MaxScore > 0 ? msg.MaxScore : 0m;
        int     passIncrement = msg.Passed ? 1 : 0;

        // ── Platform-wide counters (totalmente atómicos con raw SQL) ──────────
        int updated = await db.Database.ExecuteSqlRawAsync(
            "UPDATE analytics.platform_stats " +
            "SET total_submissions  = total_submissions  + 1, " +
            "    raw_score_sum      = raw_score_sum      + {0}, " +
            "    raw_max_score_sum  = raw_max_score_sum  + {1}, " +
            "    pass_count         = pass_count         + {2}, " +
            "    updated_at         = NOW()",
            scoreToAdd, maxScoreToAdd, passIncrement);

        if (updated == 0)
        {
            // Primera fila de platform_stats — no debería ocurrir después de enrollments,
            // pero lo manejamos por si analytics_db fue truncada parcialmente.
            db.PlatformStats.Add(new PlatformStats {
                TotalSubmissions = 1,
                RawScoreSum      = scoreToAdd,
                RawMaxScoreSum   = maxScoreToAdd,
                PassCount        = passIncrement,
                UpdatedAt        = DateTime.UtcNow
            });
            await db.SaveChangesAsync();
        }

        // ── Per-course counters ───────────────────────────────────────────────
        // IMPORTANTE: solo actualizamos si el CourseMetric ya existe.
        // La creación del CourseMetric y el conteo de TotalCourses es
        // responsabilidad exclusiva de CourseEnrolledConsumer.
        // Esto evita que submissions inflen TotalCourses en el dashboard.
        await db.Database.ExecuteSqlRawAsync(
            "UPDATE analytics.course_metrics " +
            "SET total_submissions  = total_submissions  + 1, " +
            "    raw_score_sum      = raw_score_sum      + {0}, " +
            "    raw_max_score_sum  = raw_max_score_sum  + {1}, " +
            "    pass_count         = pass_count         + {2}, " +
            "    updated_at         = NOW() " +
            "WHERE course_id = {3}",
            scoreToAdd, maxScoreToAdd, passIncrement, courseId);

        // Si el metric no existe todavía (edge case: submission llega antes del enrollment),
        // los contadores del curso quedarán desactualizados — se corregirán cuando
        // llegue el COURSE_ENROLLED correspondiente y cree el metric.
        logger.LogInformation("ASSESSMENT_SUBMITTED procesado: CourseId={CourseId} Passed={Passed}", courseId, msg.Passed);
    }
}
