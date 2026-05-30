package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evento que se publica cuando un usuario inicia sesión exitosamente en la plataforma.
 *
 * <p>Es emitido por el {@code user-service} y puede ser consumido por servicios de
 * analíticas (para registrar actividad), auditoría o detección de accesos
 * inusuales.</p>
 *
 * <p>Se identifica con el tipo {@code "USER_LOGGED_IN"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link EventoBase}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EventoUsuarioConectado extends EventoBase {

    /** Identificador único del usuario que inició sesión. */
    @JsonProperty("userId")
    private String idUsuario;

    /** Dirección de correo electrónico con la que el usuario autenticó. */
    @JsonProperty("email")
    private String correo;

    /** Rol asignado al usuario en la plataforma (p. ej. {@code "STUDENT"}, {@code "INSTRUCTOR"}). */
    @JsonProperty("role")
    private String rol;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EventoUsuarioConectado() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param idUsuario identificador único del usuario
     * @param correo    correo electrónico del usuario
     * @param rol       rol del usuario en la plataforma
     */
    public EventoUsuarioConectado(String idUsuario, String correo, String rol) {
        super("USER_LOGGED_IN", "user-service");
        this.idUsuario = idUsuario;
        this.correo    = correo;
        this.rol       = rol;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getIdUsuario() { return idUsuario; }
    public String getCorreo()    { return correo; }
    public String getRol()       { return rol; }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public void setCorreo(String correo)       { this.correo = correo; }
    public void setRol(String rol)             { this.rol = rol; }
}
