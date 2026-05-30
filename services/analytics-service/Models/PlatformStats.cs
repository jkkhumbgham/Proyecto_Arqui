namespace Puj.Analytics.Models;

/// <summary>
/// Singleton de estadísticas globales de la plataforma, actualizado en tiempo real.
///
/// <para>
/// Solo existe una fila en la tabla <c>platform_stats</c>. Todos los consumers
/// actualizan sus contadores con UPDATE atómico para evitar lost-updates
/// en escenarios de concurrencia.
/// </para>
/// </summary>
public class PlatformStats
{
    /// <summary>
    /// Identificador único del registro singleton.
    /// </summary>
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Total acumulado de usuarios registrados en la plataforma.
    /// </summary>
    public long TotalUsers { get; set; }

    /// <summary>
    /// Total acumulado de inscripciones a cursos en la plataforma.
    /// </summary>
    public long TotalEnrollments { get; set; }

    /// <summary>
    /// Total acumulado de entregas de evaluaciones en la plataforma.
    /// </summary>
    public long TotalSubmissions { get; set; }

    /// <summary>
    /// Número de cursos distintos con al menos una inscripción.
    /// </summary>
    public long TotalCourses { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes obtenidos en todas las evaluaciones.
    /// </summary>
    public decimal RawScoreSum { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes máximos posibles en todas las evaluaciones.
    /// </summary>
    public decimal RawMaxScoreSum { get; set; }

    /// <summary>
    /// Número total de entregas con resultado aprobatorio.
    /// </summary>
    public long PassCount { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) de la última modificación del registro.
    /// </summary>
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// Promedio de puntajes en porcentaje, calculado como
    /// <c>RawScoreSum / RawMaxScoreSum * 100</c>.
    /// Retorna <c>0</c> si <see cref="RawMaxScoreSum"/> es cero.
    /// </summary>
    public decimal AvgScore
        => RawMaxScoreSum > 0
            ? Math.Round(RawScoreSum / RawMaxScoreSum * 100, 2)
            : 0m;

    /// <summary>
    /// Tasa de aprobación en porcentaje, calculada como
    /// <c>PassCount / TotalSubmissions * 100</c>.
    /// Retorna <c>0</c> si no hay entregas.
    /// </summary>
    public decimal PassRate
        => TotalSubmissions > 0
            ? Math.Round((decimal)PassCount * 100 / TotalSubmissions, 2)
            : 0m;
}
