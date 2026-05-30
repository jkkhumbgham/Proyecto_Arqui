package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evento que se publica cuando un estudiante se inscribe exitosamente a un curso.
 *
 * <p>Es emitido por el {@code course-service} e incluye el identificador de la
 * inscripción generada, el identificador del estudiante y los datos básicos del
 * curso en el que se inscribió.</p>
 *
 * <p>Se identifica con el tipo {@code "COURSE_ENROLLED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link EventoBase}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EventoMatriculaCurso extends EventoBase {

    /** Identificador único de la inscripción registrada en el sistema. */
    @JsonProperty("enrollmentId")
    private String idMatricula;

    /** Identificador del estudiante que se inscribió al curso. */
    @JsonProperty("userId")
    private String idUsuario;

    /** Identificador del curso en el que se realizó la inscripción. */
    @JsonProperty("courseId")
    private String idCurso;

    /** Título legible del curso en el momento de la inscripción. */
    @JsonProperty("courseTitle")
    private String tituloCurso;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EventoMatriculaCurso() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param idMatricula identificador único de la inscripción
     * @param idUsuario   identificador del estudiante inscrito
     * @param idCurso     identificador del curso
     * @param tituloCurso título del curso en el momento de la inscripción
     */
    public EventoMatriculaCurso(
            String idMatricula,
            String idUsuario,
            String idCurso,
            String tituloCurso) {
        super("COURSE_ENROLLED", "course-service");
        this.idMatricula = idMatricula;
        this.idUsuario   = idUsuario;
        this.idCurso     = idCurso;
        this.tituloCurso = tituloCurso;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getIdMatricula() { return idMatricula; }
    public String getIdUsuario()   { return idUsuario; }
    public String getIdCurso()     { return idCurso; }
    public String getTituloCurso() { return tituloCurso; }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setIdMatricula(String idMatricula) { this.idMatricula = idMatricula; }
    public void setIdUsuario(String idUsuario)     { this.idUsuario = idUsuario; }
    public void setIdCurso(String idCurso)         { this.idCurso = idCurso; }
    public void setTituloCurso(String tituloCurso) { this.tituloCurso = tituloCurso; }
}
