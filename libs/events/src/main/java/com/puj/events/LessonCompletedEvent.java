package com.puj.events;

/**
 * Evento que se publica cuando un estudiante completa una lección de un curso.
 *
 * <p>Es emitido por el {@code course-service} y permite a otros microservicios
 * (analíticas, gamificación, seguimiento de progreso) reaccionar ante el avance
 * del estudiante dentro del contenido del curso.</p>
 *
 * <p>Se identifica con el tipo {@code "LESSON_COMPLETED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link BaseEvent}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class LessonCompletedEvent extends BaseEvent {

    /** Identificador del estudiante que completó la lección. */
    private String userId;

    /** Identificador de la lección completada. */
    private String lessonId;

    /** Identificador del curso al que pertenece la lección. */
    private String courseId;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public LessonCompletedEvent() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param userId   identificador del estudiante que completó la lección
     * @param lessonId identificador de la lección completada
     * @param courseId identificador del curso al que pertenece la lección
     */
    public LessonCompletedEvent(String userId, String lessonId, String courseId) {
        super("LESSON_COMPLETED", "course-service");
        this.userId   = userId;
        this.lessonId = lessonId;
        this.courseId = courseId;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador del estudiante que completó la lección.
     *
     * @return ID del usuario
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Devuelve el identificador de la lección completada.
     *
     * @return ID de la lección
     */
    public String getLessonId() {
        return lessonId;
    }

    /**
     * Devuelve el identificador del curso al que pertenece la lección.
     *
     * @return ID del curso
     */
    public String getCourseId() {
        return courseId;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador del estudiante.
     *
     * @param userId ID del usuario
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Establece el identificador de la lección completada.
     *
     * @param lessonId ID de la lección
     */
    public void setLessonId(String lessonId) {
        this.lessonId = lessonId;
    }

    /**
     * Establece el identificador del curso.
     *
     * @param courseId ID del curso
     */
    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }
}
