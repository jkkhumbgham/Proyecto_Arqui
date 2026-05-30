package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Evento que se publica cuando un estudiante entrega y califica una evaluación.
 *
 * <p>Este evento es emitido por el {@code assessment-service} e incluye el resultado
 * de la calificación (puntaje obtenido vs. puntaje máximo), si el estudiante aprobó,
 * la duración del intento y si con esta entrega ya aprobó <em>todas</em> las
 * evaluaciones del curso.</p>
 *
 * <p>Se identifica con el tipo {@code "ASSESSMENT_SUBMITTED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link EventoBase}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EventoEvaluacionEntregada extends EventoBase {

    /** Identificador único de la entrega registrada en el sistema. */
    @JsonProperty("submissionId")
    private String     idEntrega;

    /** Identificador del estudiante que realizó la evaluación. */
    @JsonProperty("userId")
    private String     idUsuario;

    /** Identificador de la evaluación respondida. */
    @JsonProperty("assessmentId")
    private String     idEvaluacion;

    /** Identificador del curso al que pertenece la evaluación. */
    @JsonProperty("courseId")
    private String     idCurso;

    /** Identificador de la lección a la que pertenece la evaluación. */
    @JsonProperty("lessonId")
    private String     idLeccion;

    /** Puntaje obtenido por el estudiante en esta entrega. */
    @JsonProperty("score")
    private BigDecimal puntaje;

    /** Puntaje máximo posible de la evaluación. */
    @JsonProperty("maxScore")
    private BigDecimal puntajeMaximo;

    /** Indica si el estudiante aprobó la evaluación según el umbral configurado. */
    @JsonProperty("passed")
    private boolean    aprobado;

    /** Duración del intento en segundos. */
    @JsonProperty("durationSeconds")
    private long       duracionSegundos;

    /**
     * Indica si el estudiante ha aprobado todas las evaluaciones del curso
     * tras esta entrega.
     */
    @JsonProperty("allAssessmentsPassed")
    private boolean    todasAprobadas;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EventoEvaluacionEntregada() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param idEntrega       identificador único de la entrega
     * @param idUsuario       identificador del estudiante
     * @param idEvaluacion    identificador de la evaluación
     * @param idCurso         identificador del curso
     * @param idLeccion       identificador de la lección
     * @param puntaje         puntaje obtenido por el estudiante
     * @param puntajeMaximo   puntaje máximo posible
     * @param aprobado        {@code true} si el estudiante aprobó
     * @param duracionSegundos duración del intento en segundos
     * @param todasAprobadas  {@code true} si aprobó todas las evaluaciones del curso
     */
    public EventoEvaluacionEntregada(
            String idEntrega,
            String idUsuario,
            String idEvaluacion,
            String idCurso,
            String idLeccion,
            BigDecimal puntaje,
            BigDecimal puntajeMaximo,
            boolean aprobado,
            long duracionSegundos,
            boolean todasAprobadas) {
        super("ASSESSMENT_SUBMITTED", "assessment-service");
        this.idEntrega        = idEntrega;
        this.idUsuario        = idUsuario;
        this.idEvaluacion     = idEvaluacion;
        this.idCurso          = idCurso;
        this.idLeccion        = idLeccion;
        this.puntaje          = puntaje;
        this.puntajeMaximo    = puntajeMaximo;
        this.aprobado         = aprobado;
        this.duracionSegundos = duracionSegundos;
        this.todasAprobadas   = todasAprobadas;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String     getIdEntrega()        { return idEntrega; }
    public String     getIdUsuario()        { return idUsuario; }
    public String     getIdEvaluacion()     { return idEvaluacion; }
    public String     getIdCurso()          { return idCurso; }
    public String     getIdLeccion()        { return idLeccion; }
    public BigDecimal getPuntaje()          { return puntaje; }
    public BigDecimal getPuntajeMaximo()    { return puntajeMaximo; }
    public boolean    isAprobado()          { return aprobado; }
    public long       getDuracionSegundos() { return duracionSegundos; }
    public boolean    isTodasAprobadas()    { return todasAprobadas; }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setIdEntrega(String idEntrega)               { this.idEntrega = idEntrega; }
    public void setIdUsuario(String idUsuario)               { this.idUsuario = idUsuario; }
    public void setIdEvaluacion(String idEvaluacion)         { this.idEvaluacion = idEvaluacion; }
    public void setIdCurso(String idCurso)                   { this.idCurso = idCurso; }
    public void setIdLeccion(String idLeccion)               { this.idLeccion = idLeccion; }
    public void setPuntaje(BigDecimal puntaje)               { this.puntaje = puntaje; }
    public void setPuntajeMaximo(BigDecimal puntajeMaximo)   { this.puntajeMaximo = puntajeMaximo; }
    public void setAprobado(boolean aprobado)                { this.aprobado = aprobado; }
    public void setDuracionSegundos(long duracionSegundos)   { this.duracionSegundos = duracionSegundos; }
    public void setTodasAprobadas(boolean todasAprobadas)    { this.todasAprobadas = todasAprobadas; }
}
