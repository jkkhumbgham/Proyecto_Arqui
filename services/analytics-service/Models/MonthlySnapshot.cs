using System.Text.Json.Serialization;

namespace Puj.Analytics.Models;

/// <summary>
/// Fotografía mensual de las estadísticas globales de la plataforma.
///
/// <para>
/// Generada automáticamente el primer día de cada mes por
/// <see cref="Puj.Analytics.Services.TrabajoResumenMensual"/>. Registra
/// tanto los acumulados totales al cierre del mes como la actividad
/// nueva generada exclusivamente durante ese mes.
/// </para>
/// </summary>
public class ResumenMensual
{
    /// <summary>
    /// Identificador único del snapshot.
    /// </summary>
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Año calendario al que corresponde este snapshot (p. ej. 2026).
    /// </summary>
    [JsonPropertyName("year")]
    public int Anio { get; set; }

    /// <summary>
    /// Mes calendario al que corresponde este snapshot (1–12).
    /// </summary>
    [JsonPropertyName("month")]
    public int Mes { get; set; }

    // ── Acumulados hasta el cierre del mes ───────────────────────────────────

    /// <summary>
    /// Total acumulado de usuarios registrados hasta el cierre del mes.
    /// </summary>
    [JsonPropertyName("totalUsers")]
    public long TotalUsuarios { get; set; }

    /// <summary>
    /// Total acumulado de inscripciones hasta el cierre del mes.
    /// </summary>
    [JsonPropertyName("totalEnrollments")]
    public long TotalInscripciones { get; set; }

    /// <summary>
    /// Total acumulado de entregas de evaluaciones hasta el cierre del mes.
    /// </summary>
    [JsonPropertyName("totalSubmissions")]
    public long TotalEntregas { get; set; }

    /// <summary>
    /// Total acumulado de cursos activos hasta el cierre del mes.
    /// </summary>
    [JsonPropertyName("totalCourses")]
    public long TotalCursos { get; set; }

    /// <summary>
    /// Promedio de puntajes en porcentaje acumulado hasta el cierre del mes.
    /// </summary>
    [JsonPropertyName("avgScore")]
    public decimal PuntajePromedio { get; set; }

    /// <summary>
    /// Tasa de aprobación en porcentaje acumulada hasta el cierre del mes.
    /// </summary>
    [JsonPropertyName("passRate")]
    public decimal TasaAprobacion { get; set; }

    // ── Actividad nueva durante el mes ───────────────────────────────────────

    /// <summary>
    /// Usuarios nuevos registrados exclusivamente durante este mes.
    /// </summary>
    [JsonPropertyName("newUsersThisMonth")]
    public long UsuariosNuevosEsteMes { get; set; }

    /// <summary>
    /// Inscripciones nuevas realizadas exclusivamente durante este mes.
    /// </summary>
    [JsonPropertyName("newEnrollmentsThisMonth")]
    public long InscripcionesNuevasEsteMes { get; set; }

    /// <summary>
    /// Entregas de evaluaciones realizadas exclusivamente durante este mes.
    /// </summary>
    [JsonPropertyName("newSubmissionsThisMonth")]
    public long EntregasNuevasEsteMes { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) en que se generó este snapshot.
    /// </summary>
    [JsonPropertyName("generatedAt")]
    public DateTime GeneradoEn { get; set; } = DateTime.UtcNow;
}
