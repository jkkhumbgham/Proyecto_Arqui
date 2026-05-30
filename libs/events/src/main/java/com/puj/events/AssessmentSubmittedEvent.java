package com.puj.events;

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
 * mensajería y en la deserialización polimórfica de {@link BaseEvent}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class AssessmentSubmittedEvent extends BaseEvent {

    /** Identificador único de la entrega registrada en el sistema. */
    private String     submissionId;

    /** Identificador del estudiante que realizó la evaluación. */
    private String     userId;

    /** Identificador de la evaluación respondida. */
    private String     assessmentId;

    /** Identificador del curso al que pertenece la evaluación. */
    private String     courseId;

    /** Identificador de la lección a la que pertenece la evaluación. */
    private String     lessonId;

    /** Puntaje obtenido por el estudiante en esta entrega. */
    private BigDecimal score;

    /** Puntaje máximo posible de la evaluación. */
    private BigDecimal maxScore;

    /** Indica si el estudiante aprobó la evaluación según el umbral configurado. */
    private boolean    passed;

    /** Duración del intento en segundos. */
    private long       durationSeconds;

    /**
     * Indica si el estudiante ha aprobado todas las evaluaciones del curso
     * tras esta entrega.
     */
    private boolean    allAssessmentsPassed;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public AssessmentSubmittedEvent() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param submissionId        identificador único de la entrega
     * @param userId              identificador del estudiante
     * @param assessmentId        identificador de la evaluación
     * @param courseId            identificador del curso
     * @param lessonId            identificador de la lección
     * @param score               puntaje obtenido por el estudiante
     * @param maxScore            puntaje máximo posible
     * @param passed              {@code true} si el estudiante aprobó
     * @param durationSeconds     duración del intento en segundos
     * @param allAssessmentsPassed {@code true} si aprobó todas las evaluaciones del curso
     */
    public AssessmentSubmittedEvent(
            String submissionId,
            String userId,
            String assessmentId,
            String courseId,
            String lessonId,
            BigDecimal score,
            BigDecimal maxScore,
            boolean passed,
            long durationSeconds,
            boolean allAssessmentsPassed) {
        super("ASSESSMENT_SUBMITTED", "assessment-service");
        this.submissionId         = submissionId;
        this.userId               = userId;
        this.assessmentId         = assessmentId;
        this.courseId             = courseId;
        this.lessonId             = lessonId;
        this.score                = score;
        this.maxScore             = maxScore;
        this.passed               = passed;
        this.durationSeconds      = durationSeconds;
        this.allAssessmentsPassed = allAssessmentsPassed;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único de la entrega.
     *
     * @return ID de la entrega
     */
    public String getSubmissionId() {
        return submissionId;
    }

    /**
     * Devuelve el identificador del estudiante que realizó la evaluación.
     *
     * @return ID del usuario
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Devuelve el identificador de la evaluación respondida.
     *
     * @return ID de la evaluación
     */
    public String getAssessmentId() {
        return assessmentId;
    }

    /**
     * Devuelve el identificador del curso al que pertenece la evaluación.
     *
     * @return ID del curso
     */
    public String getCourseId() {
        return courseId;
    }

    /**
     * Devuelve el identificador de la lección a la que pertenece la evaluación.
     *
     * @return ID de la lección
     */
    public String getLessonId() {
        return lessonId;
    }

    /**
     * Devuelve el puntaje obtenido por el estudiante.
     *
     * @return puntaje obtenido
     */
    public BigDecimal getScore() {
        return score;
    }

    /**
     * Devuelve el puntaje máximo posible de la evaluación.
     *
     * @return puntaje máximo
     */
    public BigDecimal getMaxScore() {
        return maxScore;
    }

    /**
     * Indica si el estudiante aprobó la evaluación.
     *
     * @return {@code true} si el resultado es aprobatorio
     */
    public boolean isPassed() {
        return passed;
    }

    /**
     * Devuelve la duración del intento de evaluación en segundos.
     *
     * @return duración en segundos
     */
    public long getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Indica si el estudiante aprobó todas las evaluaciones del curso
     * tras esta entrega.
     *
     * @return {@code true} si todas las evaluaciones del curso están aprobadas
     */
    public boolean isAllAssessmentsPassed() {
        return allAssessmentsPassed;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador de la entrega.
     *
     * @param submissionId ID de la entrega
     */
    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    /**
     * Establece el identificador del estudiante.
     *
     * @param userId ID del usuario
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Establece el identificador de la evaluación.
     *
     * @param assessmentId ID de la evaluación
     */
    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    /**
     * Establece el identificador del curso.
     *
     * @param courseId ID del curso
     */
    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    /**
     * Establece el identificador de la lección.
     *
     * @param lessonId ID de la lección
     */
    public void setLessonId(String lessonId) {
        this.lessonId = lessonId;
    }

    /**
     * Establece el puntaje obtenido.
     *
     * @param score puntaje del estudiante
     */
    public void setScore(BigDecimal score) {
        this.score = score;
    }

    /**
     * Establece el puntaje máximo de la evaluación.
     *
     * @param maxScore puntaje máximo posible
     */
    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    /**
     * Establece si el resultado es aprobatorio.
     *
     * @param passed {@code true} si el estudiante aprobó
     */
    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    /**
     * Establece la duración del intento en segundos.
     *
     * @param durationSeconds duración en segundos
     */
    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /**
     * Establece si el estudiante ha aprobado todas las evaluaciones del curso.
     *
     * @param allAssessmentsPassed {@code true} si todas las evaluaciones están aprobadas
     */
    public void setAllAssessmentsPassed(boolean allAssessmentsPassed) {
        this.allAssessmentsPassed = allAssessmentsPassed;
    }
}
