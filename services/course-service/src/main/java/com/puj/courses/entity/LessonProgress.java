package com.puj.courses.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que registra la finalización de una {@link Lesson lección} por parte de un usuario.
 *
 * <p>Existe una restricción de unicidad {@code (user_id, lesson_id)} que garantiza que
 * cada estudiante sólo puede completar una lección una vez. La marca temporal
 * {@code completedAt} se establece automáticamente en {@link #onCreate()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(
        name              = "lesson_progress",
        schema            = "courses",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_progress_user_lesson",
                columnNames = {"user_id", "lesson_id"}))
public class LessonProgress {

    /** Identificador único del registro de progreso, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identificador del usuario que completó la lección. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Lección que fue completada por el usuario. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "lesson_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_progress_lesson"))
    private Lesson lesson;

    /**
     * Identificador del curso al que pertenece la lección.
     * Desnormalizado para facilitar consultas de progreso por curso sin joins adicionales.
     */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Marca temporal del momento en que se completó la lección; inmutable. */
    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code completedAt} antes de la primera persistencia.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        completedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del registro de progreso */
    public UUID    getId()          { return id; }

    /** @return identificador del usuario que completó la lección */
    public UUID    getUserId()      { return userId; }

    /** @return lección completada */
    public Lesson  getLesson()      { return lesson; }

    /** @return identificador del curso al que pertenece la lección */
    public UUID    getCourseId()    { return courseId; }

    /** @return marca temporal de finalización de la lección */
    public Instant getCompletedAt() { return completedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador del usuario que completó la lección.
     *
     * @param userId UUID del estudiante; no debe ser {@code null}
     */
    public void setUserId(UUID userId)      { this.userId = userId; }

    /**
     * Asigna la lección completada.
     *
     * @param lesson lección finalizada por el usuario; no debe ser {@code null}
     */
    public void setLesson(Lesson lesson)    { this.lesson = lesson; }

    /**
     * Establece el identificador del curso al que pertenece la lección.
     *
     * @param courseId UUID del curso; no debe ser {@code null}
     */
    public void setCourseId(UUID courseId)  { this.courseId = courseId; }
}
