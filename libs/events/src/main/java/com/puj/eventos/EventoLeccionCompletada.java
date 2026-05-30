package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evento que se publica cuando un estudiante completa una lección de un curso.
 *
 * <p>Es emitido por el {@code course-service} y permite a otros microservicios
 * (analíticas, gamificación, seguimiento de progreso) reaccionar ante el avance
 * del estudiante dentro del contenido del curso.</p>
 *
 * <p>Se identifica con el tipo {@code "LESSON_COMPLETED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link EventoBase}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EventoLeccionCompletada extends EventoBase {

    /** Identificador del estudiante que completó la lección. */
    @JsonProperty("userId")
    private String idUsuario;

    /** Identificador de la lección completada. */
    @JsonProperty("lessonId")
    private String idLeccion;

    /** Identificador del curso al que pertenece la lección. */
    @JsonProperty("courseId")
    private String idCurso;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EventoLeccionCompletada() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param idUsuario  identificador del estudiante que completó la lección
     * @param idLeccion  identificador de la lección completada
     * @param idCurso    identificador del curso al que pertenece la lección
     */
    public EventoLeccionCompletada(String idUsuario, String idLeccion, String idCurso) {
        super("LESSON_COMPLETED", "course-service");
        this.idUsuario  = idUsuario;
        this.idLeccion  = idLeccion;
        this.idCurso    = idCurso;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getIdUsuario() { return idUsuario; }
    public String getIdLeccion() { return idLeccion; }
    public String getIdCurso()   { return idCurso; }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public void setIdLeccion(String idLeccion) { this.idLeccion = idLeccion; }
    public void setIdCurso(String idCurso)     { this.idCurso = idCurso; }
}
