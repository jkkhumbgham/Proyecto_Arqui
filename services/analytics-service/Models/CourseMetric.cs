namespace Puj.Analytics.Models;

/// <summary>
/// Métricas agregadas de un curso individual, identificado por <see cref="CourseId"/>.
///
/// <para>
/// Una fila por curso distinto. Los contadores se actualizan de forma atómica
/// mediante UPDATE de SQL crudo para soportar concurrencia sin lost-updates.
/// </para>
/// </summary>
public class CourseMetric
{
    /// <summary>
    /// Identificador único del registro de métricas.
    /// </summary>
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Identificador del curso al que pertenecen estas métricas.
    /// Tiene índice único en la base de datos.
    /// </summary>
    public Guid CourseId { get; set; }

    /// <summary>
    /// Título legible del curso, copiado del evento de inscripción.
    /// </summary>
    public string CourseTitle { get; set; } = string.Empty;

    /// <summary>
    /// Número acumulado de inscripciones al curso.
    /// </summary>
    public long TotalEnrollments { get; set; }

    /// <summary>
    /// Número acumulado de entregas de evaluaciones en el curso.
    /// </summary>
    public long TotalSubmissions { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes obtenidos en las evaluaciones del curso.
    /// </summary>
    public decimal RawScoreSum { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes máximos posibles en las evaluaciones del curso.
    /// </summary>
    public decimal RawMaxScoreSum { get; set; }

    /// <summary>
    /// Número de entregas con resultado aprobatorio en el curso.
    /// </summary>
    public long PassCount { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) de la última actualización del registro.
    /// </summary>
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// Promedio de puntajes en porcentaje para el curso, calculado como
    /// <c>RawScoreSum / RawMaxScoreSum * 100</c>.
    /// Retorna <c>0</c> si <see cref="RawMaxScoreSum"/> es cero.
    /// </summary>
    public decimal AverageScore
        => RawMaxScoreSum > 0
            ? Math.Round(RawScoreSum / RawMaxScoreSum * 100, 2)
            : 0m;

    /// <summary>
    /// Tasa de aprobación en porcentaje para el curso, calculada como
    /// <c>PassCount / TotalSubmissions * 100</c>.
    /// Retorna <c>0</c> si no hay entregas registradas.
    /// </summary>
    public decimal PassRate
        => TotalSubmissions > 0
            ? Math.Round((decimal)PassCount * 100 / TotalSubmissions, 2)
            : 0m;
}
