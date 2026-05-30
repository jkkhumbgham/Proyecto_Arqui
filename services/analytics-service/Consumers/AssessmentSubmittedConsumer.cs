using MassTransit;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Messages;
using Puj.Analytics.Models;

namespace Puj.Analytics.Consumers;

/// <summary>
/// Consumer de MassTransit que procesa el evento <c>ASSESSMENT_SUBMITTED</c>.
///
/// <para>
/// Actualiza de forma atómica los contadores de plataforma
/// (<see cref="PlatformStats"/>) y por curso (<see cref="CourseMetric"/>)
/// cuando un estudiante entrega una evaluación. Todos los UPDATE se
/// realizan con SQL crudo para garantizar incrementos atómicos y evitar
/// lost-updates en escenarios de alta concurrencia.
/// </para>
/// </summary>
/// <param name="db">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class AssessmentSubmittedConsumer(
    AnalyticsDbContext db,
    ILogger<AssessmentSubmittedConsumer> logger)
    : IConsumer<AssessmentSubmittedMessage>
{
    /// <summary>
    /// Procesa un mensaje <see cref="AssessmentSubmittedMessage"/> recibido
    /// desde RabbitMQ e incrementa los contadores de entregas, puntajes y
    /// aprobaciones tanto a nivel de plataforma como de curso.
    /// </summary>
    /// <param name="context">
    /// Contexto de consumo de MassTransit que contiene el mensaje.
    /// </param>
    /// <returns>Una tarea que representa la operación asíncrona.</returns>
    public async Task Consume(ConsumeContext<AssessmentSubmittedMessage> context)
    {
        var msg = context.Message;
        logger.LogInformation(
            "Processing ASSESSMENT_SUBMITTED: SubmissionId={SubmissionId} " +
            "CourseId={CourseId} Score={Score}/{MaxScore} Passed={Passed}",
            msg.SubmissionId, msg.CourseId, msg.Score, msg.MaxScore, msg.Passed);

        if (string.IsNullOrWhiteSpace(msg.CourseId)
            || !Guid.TryParse(msg.CourseId, out var courseId))
        {
            logger.LogWarning(
                "ASSESSMENT_SUBMITTED descartado — CourseId inválido o nulo: '{CourseId}'",
                msg.CourseId);
            return;
        }

        decimal scoreToAdd    = msg.MaxScore > 0 ? msg.Score    : 0m;
        decimal maxScoreToAdd = msg.MaxScore > 0 ? msg.MaxScore : 0m;
        int     passIncrement = msg.Passed ? 1 : 0;

        // ── Contadores globales de plataforma (UPDATE atómico con raw SQL) ────
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
            // Primera fila de platform_stats — no debería ocurrir después de
            // enrollments, pero se maneja por si analytics_db fue truncada.
            db.PlatformStats.Add(new PlatformStats {
                TotalSubmissions = 1,
                RawScoreSum      = scoreToAdd,
                RawMaxScoreSum   = maxScoreToAdd,
                PassCount        = passIncrement,
                UpdatedAt        = DateTime.UtcNow
            });
            await db.SaveChangesAsync();
        }

        // ── Contadores por curso (solo actualiza si el metric ya existe) ──────
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

        // Edge case: si el metric no existe aún (submission llega antes del
        // enrollment), los contadores quedarán desactualizados hasta que
        // llegue el COURSE_ENROLLED y cree el metric.
        logger.LogInformation(
            "ASSESSMENT_SUBMITTED procesado: CourseId={CourseId} Passed={Passed}",
            courseId, msg.Passed);
    }
}
