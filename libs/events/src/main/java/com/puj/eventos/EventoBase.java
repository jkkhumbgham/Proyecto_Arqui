package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Clase base abstracta para todos los eventos de dominio de la plataforma PUJ.
 *
 * <p>Provee los campos comunes de trazabilidad (identificador único, tipo de evento,
 * marca de tiempo y servicio de origen) y configura la deserialización polimórfica
 * de Jackson mediante {@code @JsonTypeInfo} y {@code @JsonSubTypes}, de modo que un
 * consumidor pueda reconstruir el subtipo correcto a partir del campo {@code eventType}
 * presente en el JSON.</p>
 *
 * <p>Todo evento concreto debe extender esta clase e invocar
 * {@link #EventoBase(String, String)} pasando su tipo y el nombre del servicio emisor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EventoUsuarioRegistrado.class,  name = "USER_REGISTERED"),
        @JsonSubTypes.Type(value = EventoUsuarioConectado.class,   name = "USER_LOGGED_IN"),
        @JsonSubTypes.Type(value = EventoMatriculaCurso.class,     name = "COURSE_ENROLLED"),
        @JsonSubTypes.Type(value = EventoEvaluacionEntregada.class, name = "ASSESSMENT_SUBMITTED"),
        @JsonSubTypes.Type(value = EventoNotificacionCorreo.class,  name = "EMAIL_NOTIFICATION"),
        @JsonSubTypes.Type(value = EventoLeccionCompletada.class,   name = "LESSON_COMPLETED")
})
public abstract class EventoBase {

    /** Identificador único del evento generado con {@link UUID#randomUUID()}. */
    @JsonProperty("eventId")
    private String  idEvento;

    /** Discriminador de tipo usado por Jackson para la deserialización polimórfica. */
    private String  eventType;

    /** Marca de tiempo UTC en la que ocurrió el evento. */
    @JsonProperty("occurredAt")
    private Instant ocurridoEn;

    /** Nombre del microservicio que originó el evento. */
    @JsonProperty("sourceService")
    private String  servicioOrigen;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     *
     * <p>Asigna un nuevo {@code idEvento} aleatorio y registra el instante actual
     * como {@code ocurridoEn}.</p>
     */
    protected EventoBase() {
        this.idEvento   = UUID.randomUUID().toString();
        this.ocurridoEn = Instant.now();
    }

    /**
     * Constructor principal que inicializa los campos de trazabilidad del evento.
     *
     * @param eventType      identificador de tipo del evento (p. ej. {@code "USER_REGISTERED"})
     * @param servicioOrigen nombre del microservicio emisor (p. ej. {@code "user-service"})
     */
    protected EventoBase(String eventType, String servicioOrigen) {
        this();
        this.eventType      = eventType;
        this.servicioOrigen = servicioOrigen;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único del evento.
     *
     * @return cadena UUID que identifica unívocamente este evento
     */
    public String getIdEvento() {
        return idEvento;
    }

    /**
     * Devuelve el tipo de evento como cadena constante.
     *
     * @return tipo del evento (p. ej. {@code "USER_REGISTERED"})
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Devuelve el instante UTC en que se produjo el evento.
     *
     * @return marca de tiempo de ocurrencia del evento
     */
    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    /**
     * Devuelve el nombre del microservicio que generó el evento.
     *
     * @return nombre del servicio de origen
     */
    public String getServicioOrigen() {
        return servicioOrigen;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setIdEvento(String idEvento) {
        this.idEvento = idEvento;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setOcurridoEn(Instant ocurridoEn) {
        this.ocurridoEn = ocurridoEn;
    }

    public void setServicioOrigen(String servicioOrigen) {
        this.servicioOrigen = servicioOrigen;
    }
}
