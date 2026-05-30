package com.puj.courses.entity;

/**
 * Estados posibles del ciclo de vida de un {@link Course}.
 *
 * <ul>
 *   <li>{@code DRAFT}     — el curso está en edición y no es visible para estudiantes.</li>
 *   <li>{@code PUBLISHED} — el curso está activo y acepta inscripciones.</li>
 *   <li>{@code ARCHIVED}  — el curso ha sido archivado y no acepta nuevas inscripciones.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public enum CourseStatus {
    /** Curso en modo borrador, no visible para estudiantes. */
    DRAFT,

    /** Curso publicado, disponible para inscripción. */
    PUBLISHED,

    /** Curso archivado, sin admisión de nuevos estudiantes. */
    ARCHIVED
}
