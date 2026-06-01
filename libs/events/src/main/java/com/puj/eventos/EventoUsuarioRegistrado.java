package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evento que se publica cuando un nuevo usuario completa su registro en la plataforma.
 *
 * <p>Es emitido por el {@code user-service} e incluye los datos de identidad del
 * usuario recién creado. Puede ser consumido por el servicio de correo para enviar
 * un mensaje de bienvenida, por el servicio de analíticas para registrar el alta, o
 * por cualquier otro componente interesado en nuevos usuarios.</p>
 *
 * <p>Se identifica con el tipo {@code "USER_REGISTERED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link EventoBase}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EventoUsuarioRegistrado extends EventoBase {

    /** Identificador único del usuario recién registrado. */
    @JsonProperty("userId")
    private String idUsuario;

    /** Dirección de correo electrónico proporcionada durante el registro. */
    @JsonProperty("email")
    private String correo;

    /** Nombre de pila del usuario. */
    @JsonProperty("firstName")
    private String nombre;

    /** Apellido del usuario. */
    @JsonProperty("lastName")
    private String apellido;

    /** Rol inicial asignado al usuario (p. ej. {@code "STUDENT"}, {@code "INSTRUCTOR"}). */
    @JsonProperty("role")
    private String rol;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EventoUsuarioRegistrado() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param idUsuario identificador único del nuevo usuario
     * @param correo    correo electrónico del usuario
     * @param nombre    nombre de pila del usuario
     * @param apellido  apellido del usuario
     * @param rol       rol asignado en la plataforma
     */
    public EventoUsuarioRegistrado(
            String idUsuario,
            String correo,
            String nombre,
            String apellido,
            String rol) {
        super("USER_REGISTERED", "user-service");
        this.idUsuario = idUsuario;
        this.correo    = correo;
        this.nombre    = nombre;
        this.apellido  = apellido;
        this.rol       = rol;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getIdUsuario() { return idUsuario; }
    public String getCorreo()    { return correo; }
    public String getNombre()    { return nombre; }
    public String getApellido()  { return apellido; }
    public String getRol()       { return rol; }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public void setCorreo(String correo)       { this.correo = correo; }
    public void setNombre(String nombre)       { this.nombre = nombre; }
    public void setApellido(String apellido)   { this.apellido = apellido; }
    public void setRol(String rol)             { this.rol = rol; }
}
