using System.Text.Json.Serialization;

namespace Puj.Analytics.Models;

/// <summary>
/// Caché local de datos de identificación de un usuario de la plataforma.
///
/// <para>
/// Almacena nombre, correo, rol y última sesión de cada usuario para
/// evitar llamadas remotas a <c>user-service</c> durante la generación
/// de reportes de analítica. Se actualiza con los eventos
/// <c>USER_REGISTERED</c> y <c>USER_LOGGED_IN</c>.
/// </para>
/// </summary>
public class CacheNombreEstudiante
{
    /// <summary>
    /// Identificador único del registro de caché.
    /// </summary>
    [JsonPropertyName("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Identificador del usuario en la plataforma. Tiene índice único.
    /// </summary>
    [JsonPropertyName("userId")]
    public Guid IdUsuario { get; set; }

    /// <summary>
    /// Nombre completo del usuario (nombre + apellido concatenados).
    /// </summary>
    [JsonPropertyName("studentName")]
    public string NombreEstudiante { get; set; } = string.Empty;

    /// <summary>
    /// Correo electrónico del usuario.
    /// </summary>
    [JsonPropertyName("email")]
    public string Correo { get; set; } = string.Empty;

    /// <summary>
    /// Rol del usuario en la plataforma (p. ej. STUDENT, INSTRUCTOR, ADMIN).
    /// </summary>
    [JsonPropertyName("role")]
    public string Rol { get; set; } = string.Empty;

    /// <summary>
    /// Fecha y hora (UTC) del último inicio de sesión registrado.
    /// <c>null</c> indica que el usuario nunca ha iniciado sesión.
    /// </summary>
    [JsonPropertyName("lastLoginAt")]
    public DateTime? UltimoAccesoEn { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) de la última actualización de este registro de caché.
    /// </summary>
    [JsonPropertyName("updatedAt")]
    public DateTime ActualizadoEn { get; set; } = DateTime.UtcNow;
}
