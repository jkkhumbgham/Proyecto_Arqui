package com.puj.cursos.matriculas.dominio;

/**
 * Estados posibles de una {@link Matricula matrícula} de estudiante en un curso.
 *
 * <ul>
 *   <li>{@code ACTIVE}    — inscripción vigente; el estudiante puede acceder al contenido.</li>
 *   <li>{@code COMPLETED} — el estudiante completó el curso con el progreso requerido.</li>
 *   <li>{@code CANCELLED} — la inscripción fue cancelada por el estudiante o un administrador.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public enum EstadoMatricula {
    /** Inscripción activa con acceso al contenido del curso. */
    ACTIVE,

    /** Inscripción marcada como completada tras alcanzar el 100% de progreso. */
    COMPLETED,

    /** Inscripción cancelada; aplica soft-delete en la entidad. */
    CANCELLED
}
