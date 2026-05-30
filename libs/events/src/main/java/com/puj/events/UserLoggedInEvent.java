package com.puj.events;

/**
 * Evento que se publica cuando un usuario inicia sesión exitosamente en la plataforma.
 *
 * <p>Es emitido por el {@code user-service} y puede ser consumido por servicios de
 * analíticas (para registrar actividad), auditoría o detección de accesos
 * inusuales.</p>
 *
 * <p>Se identifica con el tipo {@code "USER_LOGGED_IN"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link BaseEvent}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class UserLoggedInEvent extends BaseEvent {

    /** Identificador único del usuario que inició sesión. */
    private String userId;

    /** Dirección de correo electrónico con la que el usuario autenticó. */
    private String email;

    /** Rol asignado al usuario en la plataforma (p. ej. {@code "STUDENT"}, {@code "INSTRUCTOR"}). */
    private String role;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public UserLoggedInEvent() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param userId identificador único del usuario
     * @param email  correo electrónico del usuario
     * @param role   rol del usuario en la plataforma
     */
    public UserLoggedInEvent(String userId, String email, String role) {
        super("USER_LOGGED_IN", "user-service");
        this.userId = userId;
        this.email  = email;
        this.role   = role;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único del usuario que inició sesión.
     *
     * @return ID del usuario
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Devuelve la dirección de correo electrónico del usuario.
     *
     * @return correo electrónico del usuario
     */
    public String getEmail() {
        return email;
    }

    /**
     * Devuelve el rol asignado al usuario en la plataforma.
     *
     * @return rol del usuario
     */
    public String getRole() {
        return role;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador del usuario.
     *
     * @param userId ID del usuario
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Establece el correo electrónico del usuario.
     *
     * @param email dirección de correo electrónico
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Establece el rol del usuario en la plataforma.
     *
     * @param role rol asignado al usuario
     */
    public void setRole(String role) {
        this.role = role;
    }
}
