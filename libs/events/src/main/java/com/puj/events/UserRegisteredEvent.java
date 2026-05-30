package com.puj.events;

/**
 * Evento que se publica cuando un nuevo usuario completa su registro en la plataforma.
 *
 * <p>Es emitido por el {@code user-service} e incluye los datos de identidad del
 * usuario recién creado. Puede ser consumido por el servicio de correo para enviar
 * un mensaje de bienvenida, por el servicio de analíticas para registrar el alta, o
 * por cualquier otro componente interesado en nuevos usuarios.</p>
 *
 * <p>Se identifica con el tipo {@code "USER_REGISTERED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link BaseEvent}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class UserRegisteredEvent extends BaseEvent {

    /** Identificador único del usuario recién registrado. */
    private String userId;

    /** Dirección de correo electrónico proporcionada durante el registro. */
    private String email;

    /** Nombre de pila del usuario. */
    private String firstName;

    /** Apellido del usuario. */
    private String lastName;

    /** Rol inicial asignado al usuario (p. ej. {@code "STUDENT"}, {@code "INSTRUCTOR"}). */
    private String role;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public UserRegisteredEvent() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param userId    identificador único del nuevo usuario
     * @param email     correo electrónico del usuario
     * @param firstName nombre de pila del usuario
     * @param lastName  apellido del usuario
     * @param role      rol asignado en la plataforma
     */
    public UserRegisteredEvent(
            String userId,
            String email,
            String firstName,
            String lastName,
            String role) {
        super("USER_REGISTERED", "user-service");
        this.userId    = userId;
        this.email     = email;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.role      = role;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único del usuario registrado.
     *
     * @return ID del usuario
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Devuelve la dirección de correo electrónico del usuario.
     *
     * @return correo electrónico
     */
    public String getEmail() {
        return email;
    }

    /**
     * Devuelve el nombre de pila del usuario.
     *
     * @return nombre de pila
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Devuelve el apellido del usuario.
     *
     * @return apellido
     */
    public String getLastName() {
        return lastName;
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
     * Establece el nombre de pila del usuario.
     *
     * @param firstName nombre de pila
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Establece el apellido del usuario.
     *
     * @param lastName apellido
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
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
