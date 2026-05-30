package com.puj.events;

/**
 * Evento que se publica cuando un estudiante se inscribe exitosamente a un curso.
 *
 * <p>Es emitido por el {@code course-service} e incluye el identificador de la
 * inscripción generada, el identificador del estudiante y los datos básicos del
 * curso en el que se inscribió.</p>
 *
 * <p>Se identifica con el tipo {@code "COURSE_ENROLLED"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link BaseEvent}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class CourseEnrolledEvent extends BaseEvent {

    /** Identificador único de la inscripción registrada en el sistema. */
    private String enrollmentId;

    /** Identificador del estudiante que se inscribió al curso. */
    private String userId;

    /** Identificador del curso en el que se realizó la inscripción. */
    private String courseId;

    /** Título legible del curso en el momento de la inscripción. */
    private String courseTitle;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public CourseEnrolledEvent() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param enrollmentId identificador único de la inscripción
     * @param userId       identificador del estudiante inscrito
     * @param courseId     identificador del curso
     * @param courseTitle  título del curso en el momento de la inscripción
     */
    public CourseEnrolledEvent(
            String enrollmentId,
            String userId,
            String courseId,
            String courseTitle) {
        super("COURSE_ENROLLED", "course-service");
        this.enrollmentId = enrollmentId;
        this.userId       = userId;
        this.courseId     = courseId;
        this.courseTitle  = courseTitle;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único de la inscripción.
     *
     * @return ID de la inscripción
     */
    public String getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Devuelve el identificador del estudiante inscrito.
     *
     * @return ID del usuario
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Devuelve el identificador del curso.
     *
     * @return ID del curso
     */
    public String getCourseId() {
        return courseId;
    }

    /**
     * Devuelve el título del curso al momento de la inscripción.
     *
     * @return título del curso
     */
    public String getCourseTitle() {
        return courseTitle;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador de la inscripción.
     *
     * @param enrollmentId ID de la inscripción
     */
    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
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
     * Establece el identificador del curso.
     *
     * @param courseId ID del curso
     */
    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    /**
     * Establece el título del curso.
     *
     * @param courseTitle título del curso
     */
    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }
}
