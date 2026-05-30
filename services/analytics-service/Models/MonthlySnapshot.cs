namespace Puj.Analytics.Models;

/// <summary>
/// Fotografía mensual de las estadísticas globales de la plataforma.
///
/// <para>
/// Generada automáticamente el primer día de cada mes por
/// <see cref="Puj.Analytics.Services.MonthlySnapshotJob"/>. Registra
/// tanto los acumulados totales al cierre del mes como la actividad
/// nueva generada exclusivamente durante ese mes.
/// </para>
/// </summary>
public class MonthlySnapshot
{
    /// <summary>
    /// Identificador único del snapshot.
    /// </summary>
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Año calendario al que corresponde este snapshot (p. ej. 2026).
    /// </summary>
    public int Year { get; set; }

    /// <summary>
    /// Mes calendario al que corresponde este snapshot (1–12).
    /// </summary>
    public int Month { get; set; }

    // ── Acumulados hasta el cierre del mes ───────────────────────────────────

    /// <summary>
    /// Total acumulado de usuarios registrados hasta el cierre del mes.
    /// </summary>
    public long TotalUsers { get; set; }

    /// <summary>
    /// Total acumulado de inscripciones hasta el cierre del mes.
    /// </summary>
    public long TotalEnrollments { get; set; }

    /// <summary>
    /// Total acumulado de entregas de evaluaciones hasta el cierre del mes.
    /// </summary>
    public long TotalSubmissions { get; set; }

    /// <summary>
    /// Total acumulado de cursos activos hasta el cierre del mes.
    /// </summary>
    public long TotalCourses { get; set; }

    /// <summary>
    /// Promedio de puntajes en porcentaje acumulado hasta el cierre del mes.
    /// </summary>
    public decimal AvgScore { get; set; }

    /// <summary>
    /// Tasa de aprobación en porcentaje acumulada hasta el cierre del mes.
    /// </summary>
    public decimal PassRate { get; set; }

    // ── Actividad nueva durante el mes ───────────────────────────────────────

    /// <summary>
    /// Usuarios nuevos registrados exclusivamente durante este mes.
    /// </summary>
    public long NewUsersThisMonth { get; set; }

    /// <summary>
    /// Inscripciones nuevas realizadas exclusivamente durante este mes.
    /// </summary>
    public long NewEnrollmentsThisMonth { get; set; }

    /// <summary>
    /// Entregas de evaluaciones realizadas exclusivamente durante este mes.
    /// </summary>
    public long NewSubmissionsThisMonth { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) en que se generó este snapshot.
    /// </summary>
    public DateTime GeneratedAt { get; set; } = DateTime.UtcNow;
}
