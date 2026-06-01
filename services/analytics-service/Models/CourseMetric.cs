using System.Text.Json.Serialization;

namespace Puj.Analytics.Models;

/// <summary>
/// Métricas agregadas de un curso individual, identificado por <see cref="IdCurso"/>.
///
/// <para>
/// Una fila por curso distinto. Los contadores se actualizan de forma atómica
/// mediante UPDATE de SQL crudo para soportar concurrencia sin lost-updates.
/// </para>
/// </summary>
public class MetricaCurso
{
    /// <summary>
    /// Identificador único del registro de métricas.
    /// </summary>
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Identificador del curso al que pertenecen estas métricas.
    /// Tiene índice único en la base de datos.
    /// </summary>
    [JsonPropertyName("courseId")]
    public Guid IdCurso { get; set; }

    /// <summary>
    /// Título legible del curso, copiado del evento de inscripción.
    /// </summary>
    [JsonPropertyName("courseTitle")]
    public string TituloCurso { get; set; } = string.Empty;

    /// <summary>
    /// Número acumulado de inscripciones al curso.
    /// </summary>
    [JsonPropertyName("totalEnrollments")]
    public long TotalInscripciones { get; set; }

    /// <summary>
    /// Número acumulado de entregas de evaluaciones en el curso.
    /// </summary>
    [JsonPropertyName("totalSubmissions")]
    public long TotalEntregas { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes obtenidos en las evaluaciones del curso.
    /// </summary>
    [JsonPropertyName("rawScoreSum")]
    public decimal SumaPuntajesBrutos { get; set; }

    /// <summary>
    /// Suma acumulada de puntajes máximos posibles en las evaluaciones del curso.
    /// </summary>
    [JsonPropertyName("rawMaxScoreSum")]
    public decimal SumaMaxPuntajesBrutos { get; set; }

    /// <summary>
    /// Número de entregas con resultado aprobatorio en el curso.
    /// </summary>
    [JsonPropertyName("passCount")]
    public long ConteoAprobados { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) de la última actualización del registro.
    /// </summary>
    [JsonPropertyName("updatedAt")]
    public DateTime ActualizadoEn { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// Promedio de puntajes en porcentaje para el curso, calculado como
    /// <c>SumaPuntajesBrutos / SumaMaxPuntajesBrutos * 100</c>.
    /// Retorna <c>0</c> si <see cref="SumaMaxPuntajesBrutos"/> es cero.
    /// </summary>
    [JsonPropertyName("averageScore")]
    public decimal PuntajePromedio
        => SumaMaxPuntajesBrutos > 0
            ? Math.Round(SumaPuntajesBrutos / SumaMaxPuntajesBrutos * 100, 2)
            : 0m;

    /// <summary>
    /// Tasa de aprobación en porcentaje para el curso, calculada como
    /// <c>ConteoAprobados / TotalEntregas * 100</c>.
    /// Retorna <c>0</c> si no hay entregas registradas.
    /// </summary>
    [JsonPropertyName("passRate")]
    public decimal TasaAprobacion
        => TotalEntregas > 0
            ? Math.Round((decimal)ConteoAprobados * 100 / TotalEntregas, 2)
            : 0m;
}
