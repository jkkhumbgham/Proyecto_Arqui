package com.puj.assessments.entity;

/**
 * Estado del ciclo de vida de una {@link Submission}.
 *
 * <ul>
 *   <li>{@link #IN_PROGRESS} — el estudiante ha iniciado pero no ha enviado respuestas.</li>
 *   <li>{@link #SUBMITTED} — las respuestas fueron enviadas; pendiente de calificación.</li>
 *   <li>{@link #GRADED} — la submission fue calificada y tiene puntaje asignado.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public enum SubmissionStatus {
    /** Evaluación iniciada; el estudiante aún no ha enviado sus respuestas. */
    IN_PROGRESS,

    /** Respuestas enviadas; en espera de calificación. */
    SUBMITTED,

    /** Submission calificada con puntaje definitivo asignado. */
    GRADED
}
