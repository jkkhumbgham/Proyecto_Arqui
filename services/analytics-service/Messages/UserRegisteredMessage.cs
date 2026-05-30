namespace Puj.Analytics.Messages;

/// <summary>
/// Mensaje publicado cuando un nuevo usuario se registra en la plataforma.
///
/// <para>
/// Corresponde al evento <c>USER_REGISTERED</c> del exchange
/// <c>platform.events</c>. Consumido por
/// <see cref="Puj.Analytics.Consumers.UserRegisteredConsumer"/> para
/// incrementar <c>total_users</c> y poblar el caché local de nombres.
/// </para>
/// </summary>
/// <param name="EventId">Identificador único del evento (UUID).</param>
/// <param name="EventType">
/// Tipo del evento; siempre <c>"USER_REGISTERED"</c>.
/// </param>
/// <param name="UserId">Identificador del nuevo usuario.</param>
/// <param name="Email">Correo electrónico del nuevo usuario.</param>
/// <param name="FirstName">Nombre de pila del nuevo usuario.</param>
/// <param name="LastName">Apellido del nuevo usuario.</param>
/// <param name="Role">Rol asignado al nuevo usuario.</param>
/// <param name="OccurredAt">Fecha y hora (UTC) del evento.</param>
public record UserRegisteredMessage(
    string   EventId,
    string   EventType,
    string   UserId,
    string   Email,
    string   FirstName,
    string   LastName,
    string   Role,
    DateTime OccurredAt
);
