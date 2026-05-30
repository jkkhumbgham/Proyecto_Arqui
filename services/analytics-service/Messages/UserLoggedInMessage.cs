namespace Puj.Analytics.Messages;

/// <summary>
/// Mensaje publicado cuando un usuario inicia sesión en la plataforma.
///
/// <para>
/// Corresponde al evento <c>USER_LOGGED_IN</c> del exchange
/// <c>platform.events</c>. Consumido por
/// <see cref="Puj.Analytics.Consumers.UserLoggedInConsumer"/> para
/// actualizar <c>last_login_at</c> en el caché local de usuarios.
/// </para>
/// </summary>
/// <param name="EventId">Identificador único del evento (UUID).</param>
/// <param name="EventType">
/// Tipo del evento; siempre <c>"USER_LOGGED_IN"</c>.
/// </param>
/// <param name="UserId">Identificador del usuario que inició sesión.</param>
/// <param name="Email">Correo electrónico del usuario.</param>
/// <param name="Role">Rol del usuario (p. ej. STUDENT, INSTRUCTOR, ADMIN).</param>
/// <param name="OccurredAt">Fecha y hora (UTC) del evento.</param>
public record UserLoggedInMessage(
    string   EventId,
    string   EventType,
    string   UserId,
    string   Email,
    string   Role,
    DateTime OccurredAt
);
