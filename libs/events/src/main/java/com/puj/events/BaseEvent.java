package com.puj.events;

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
 * {@link #BaseEvent(String, String)} pasando su tipo y el nombre del servicio emisor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserRegisteredEvent.class,      name = "USER_REGISTERED"),
        @JsonSubTypes.Type(value = UserLoggedInEvent.class,        name = "USER_LOGGED_IN"),
        @JsonSubTypes.Type(value = CourseEnrolledEvent.class,      name = "COURSE_ENROLLED"),
        @JsonSubTypes.Type(value = AssessmentSubmittedEvent.class,  name = "ASSESSMENT_SUBMITTED"),
        @JsonSubTypes.Type(value = EmailNotificationEvent.class,   name = "EMAIL_NOTIFICATION"),
        @JsonSubTypes.Type(value = LessonCompletedEvent.class,     name = "LESSON_COMPLETED")
})
public abstract class BaseEvent {

    /** Identificador único del evento generado con {@link UUID#randomUUID()}. */
    private String  eventId;

    /** Discriminador de tipo usado por Jackson para la deserialización polimórfica. */
    private String  eventType;

    /** Marca de tiempo UTC en la que ocurrió el evento. */
    private Instant occurredAt;

    /** Nombre del microservicio que originó el evento. */
    private String  sourceService;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     *
     * <p>Asigna un nuevo {@code eventId} aleatorio y registra el instante actual
     * como {@code occurredAt}.</p>
     */
    protected BaseEvent() {
        this.eventId    = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
    }

    /**
     * Constructor principal que inicializa los campos de trazabilidad del evento.
     *
     * @param eventType     identificador de tipo del evento (p. ej. {@code "USER_REGISTERED"})
     * @param sourceService nombre del microservicio emisor (p. ej. {@code "user-service"})
     */
    protected BaseEvent(String eventType, String sourceService) {
        this();
        this.eventType     = eventType;
        this.sourceService = sourceService;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único del evento.
     *
     * @return cadena UUID que identifica unívocamente este evento
     */
    public String getEventId() {
        return eventId;
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
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Devuelve el nombre del microservicio que generó el evento.
     *
     * @return nombre del servicio de origen
     */
    public String getSourceService() {
        return sourceService;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador único del evento.
     *
     * @param eventId nuevo valor del identificador
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Establece el tipo de evento.
     *
     * @param eventType cadena descriptora del tipo de evento
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Establece la marca de tiempo de ocurrencia del evento.
     *
     * @param occurredAt instante UTC de ocurrencia
     */
    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    /**
     * Establece el nombre del microservicio de origen.
     *
     * @param sourceService nombre del servicio emisor
     */
    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }
}
