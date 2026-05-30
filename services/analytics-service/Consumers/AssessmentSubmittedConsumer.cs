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
/// (<see cref="EstadisticasPlataforma"/>) y por curso (<see cref="MetricaCurso"/>)
/// cuando un estudiante entrega una evaluación. Todos los UPDATE se
/// realizan con SQL crudo para garantizar incrementos atómicos y evitar
/// lost-updates en escenarios de alta concurrencia.
/// </para>
/// </summary>
/// <param name="baseDatos">Contexto de base de datos de analítica.</param>
/// <param name="logger">Logger estructurado del consumer.</param>
public class ConsumidorEvaluacionEntregada(
    ContextoBaseDatosAnaliticas baseDatos,
    ILogger<ConsumidorEvaluacionEntregada> logger)
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
        var mensaje = context.Message;
        logger.LogInformation(
            "Processing ASSESSMENT_SUBMITTED: SubmissionId={SubmissionId} " +
            "CourseId={CourseId} Score={Score}/{MaxScore} Passed={Passed}",
            mensaje.SubmissionId, mensaje.CourseId, mensaje.Score, mensaje.MaxScore, mensaje.Passed);

        if (string.IsNullOrWhiteSpace(mensaje.CourseId)
            || !Guid.TryParse(mensaje.CourseId, out var idCurso))
        {
            logger.LogWarning(
                "ASSESSMENT_SUBMITTED descartado — CourseId inválido o nulo: '{CourseId}'",
                mensaje.CourseId);
            return;
        }

        decimal puntajeAAgregar    = mensaje.MaxScore > 0 ? mensaje.Score    : 0m;
        decimal maxPuntajeAAgregar = mensaje.MaxScore > 0 ? mensaje.MaxScore : 0m;
        int     incrementoAprobado = mensaje.Passed ? 1 : 0;

        // ── Contadores globales de plataforma (UPDATE atómico con raw SQL) ────
        int filasActualizadas = await baseDatos.Database.ExecuteSqlRawAsync(
            "UPDATE analytics.platform_stats " +
            "SET total_submissions  = total_submissions  + 1, " +
            "    raw_score_sum      = raw_score_sum      + {0}, " +
            "    raw_max_score_sum  = raw_max_score_sum  + {1}, " +
            "    pass_count         = pass_count         + {2}, " +
            "    updated_at         = NOW()",
            puntajeAAgregar, maxPuntajeAAgregar, incrementoAprobado);

        if (filasActualizadas == 0)
        {
            // Primera fila de platform_stats — no debería ocurrir después de
            // enrollments, pero se maneja por si analytics_db fue truncada.
            baseDatos.EstadisticasPlataforma.Add(new EstadisticasPlataforma {
                TotalEntregas        = 1,
                SumaPuntajesBrutos   = puntajeAAgregar,
                SumaMaxPuntajesBrutos = maxPuntajeAAgregar,
                ConteoAprobados      = incrementoAprobado,
                ActualizadoEn        = DateTime.UtcNow
            });
            await baseDatos.SaveChangesAsync();
        }

        // ── Contadores por curso (solo actualiza si el metric ya existe) ──────
        // La creación del MetricaCurso y el conteo de TotalCursos es
        // responsabilidad exclusiva de ConsumidorMatriculaCurso.
        // Esto evita que submissions inflen TotalCursos en el dashboard.
        await baseDatos.Database.ExecuteSqlRawAsync(
            "UPDATE analytics.course_metrics " +
            "SET total_submissions  = total_submissions  + 1, " +
            "    raw_score_sum      = raw_score_sum      + {0}, " +
            "    raw_max_score_sum  = raw_max_score_sum  + {1}, " +
            "    pass_count         = pass_count         + {2}, " +
            "    updated_at         = NOW() " +
            "WHERE course_id = {3}",
            puntajeAAgregar, maxPuntajeAAgregar, incrementoAprobado, idCurso);

        // Edge case: si el metric no existe aún (submission llega antes del
        // enrollment), los contadores quedarán desactualizados hasta que
        // llegue el COURSE_ENROLLED y cree el metric.
        logger.LogInformation(
            "ASSESSMENT_SUBMITTED procesado: CourseId={CourseId} Passed={Passed}",
            idCurso, mensaje.Passed);
    }
}
