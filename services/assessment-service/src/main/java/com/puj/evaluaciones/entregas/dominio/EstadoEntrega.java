package com.puj.evaluaciones.entregas.dominio;

/**
 * Estado del ciclo de vida de una {@link Entrega}.
 *
 * <ul>
 *   <li>{@link #IN_PROGRESS} — el estudiante ha iniciado pero no ha enviado respuestas.</li>
 *   <li>{@link #SUBMITTED} — las respuestas fueron enviadas; pendiente de calificación.</li>
 *   <li>{@link #GRADED} — la entrega fue calificada y tiene puntaje asignado.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public enum EstadoEntrega {
    /** Evaluación iniciada; el estudiante aún no ha enviado sus respuestas. */
    IN_PROGRESS,

    /** Respuestas enviadas; en espera de calificación. */
    SUBMITTED,

    /** Entrega calificada con puntaje definitivo asignado. */
    GRADED
}
