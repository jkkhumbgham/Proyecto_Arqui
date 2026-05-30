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
public class StudentNameCache
{
    /// <summary>
    /// Identificador único del registro de caché.
    /// </summary>
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Identificador del usuario en la plataforma. Tiene índice único.
    /// </summary>
    public Guid UserId { get; set; }

    /// <summary>
    /// Nombre completo del usuario (nombre + apellido concatenados).
    /// </summary>
    public string StudentName { get; set; } = string.Empty;

    /// <summary>
    /// Correo electrónico del usuario.
    /// </summary>
    public string Email { get; set; } = string.Empty;

    /// <summary>
    /// Rol del usuario en la plataforma (p. ej. STUDENT, INSTRUCTOR, ADMIN).
    /// </summary>
    public string Role { get; set; } = string.Empty;

    /// <summary>
    /// Fecha y hora (UTC) del último inicio de sesión registrado.
    /// <c>null</c> indica que el usuario nunca ha iniciado sesión.
    /// </summary>
    public DateTime? LastLoginAt { get; set; }

    /// <summary>
    /// Fecha y hora (UTC) de la última actualización de este registro de caché.
    /// </summary>
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
