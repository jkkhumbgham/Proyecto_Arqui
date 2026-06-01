using System.Text.Json.Serialization;

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
public class EstadisticasPlataforma
{
    /// <summary>
    /// Identificador único del registro singleton.
    /// </summary>
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Total acumulado de usuarios registrados en la plataforma.
    /// </summary>
    [JsonPropertyName("totalUsers")]
    public long TotalUsuarios { get; set; }

    /// <summary>
    /// Total acumulado de inscripciones a cursos en la plataforma.
    /// </summary>
    [JsonPropertyName("totalEnrollments")]
    public long TotalInscripciones { get; set; }

    /// <summary>
    /// Total acumulado de entregas de evaluaciones en la plataforma.
    /// </summary>
    [JsonPropertyName("totalSubmissions")]
    public long TotalEntregas { get; set; }

    /// <summary>
    /// Número de cursos distintos con al menos una inscripción.
    /// </summary>
    [JsonPropertyName("totalCourses")]
    public long TotalCursos { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes obtenidos en todas las evaluaciones.
    /// </summary>
    [JsonPropertyName("rawScoreSum")]
    public decimal SumaPuntajesBrutos { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes máximos posibles en todas las evaluaciones.
    /// </summary>
    [JsonPropertyName("rawMaxScoreSum")]
    public decimal SumaMaxPuntajesBrutos { get; set; }

    /// <summary>
    /// Número total de entregas con resultado aprobatorio.
    /// </summary>
    [JsonPropertyName("passCount")]
    public long ConteoAprobados { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) de la última modificación del registro.
    /// </summary>
    [JsonPropertyName("updatedAt")]
    public DateTime ActualizadoEn { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// Promedio de puntajes en porcentaje, calculado como
    /// <c>SumaPuntajesBrutos / SumaMaxPuntajesBrutos * 100</c>.
    /// Retorna <c>0</c> si <see cref="SumaMaxPuntajesBrutos"/> es cero.
    /// </summary>
    [JsonPropertyName("avgScore")]
    public decimal PuntajePromedio
        => SumaMaxPuntajesBrutos > 0
            ? Math.Round(SumaPuntajesBrutos / SumaMaxPuntajesBrutos * 100, 2)
            : 0m;

    /// <summary>
    /// Tasa de aprobación en porcentaje, calculada como
    /// <c>ConteoAprobados / TotalEntregas * 100</c>.
    /// Retorna <c>0</c> si no hay entregas.
    /// </summary>
    [JsonPropertyName("passRate")]
    public decimal TasaAprobacion
        => TotalEntregas > 0
            ? Math.Round((decimal)ConteoAprobados * 100 / TotalEntregas, 2)
            : 0m;
}
