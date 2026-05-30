package com.puj.assessments.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Regla adaptativa determinística asociada a una evaluación.
 *
 * <p>Define el umbral de puntaje a partir del cual el motor adaptativo
 * redirige al estudiante hacia contenido suplementario. Cada regla tiene
 * restricción única por par {@code (assessment_id, course_id)}.</p>
 *
 * <p>Si el puntaje del estudiante cae por debajo de {@code scoreThresholdPct},
 * se genera un {@link com.puj.assessments.adaptive.AdaptiveRecommendation}
 * apuntando a {@code supplementaryLessonId}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "adaptive_rules", schema = "assessments",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_adaptive_rule",
                columnNames = {"assessment_id", "course_id"}))
public class AdaptiveRule {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    /** Umbral en porcentaje (0-100). Si {@code score < threshold} → contenido suplementario. */
    @Column(name = "score_threshold_pct", nullable = false)
    private double scoreThresholdPct;

    /** Lección de refuerzo a la que se redirige cuando el estudiante no supera el umbral. */
    @Column(name = "supplementary_lesson_id")
    private UUID supplementaryLessonId;

    /** Mensaje personalizado del instructor para motivar al estudiante. */
    @Column(name = "message", length = 500)
    private String message;

    /** Indica si la regla está activa y debe evaluarse. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Inicializa el ID y las marcas de tiempo al persistir por primera vez.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Actualiza la marca de tiempo de modificación en cada actualización.
     */
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /**
     * Indica si la regla ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca la regla como eliminada de forma lógica con la marca de tiempo actual.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único de la regla */
    public UUID    getId()                    { return id; }

    /** @return identificador de la evaluación a la que aplica esta regla */
    public UUID    getAssessmentId()          { return assessmentId; }

    /** @return identificador del curso al que pertenece la regla */
    public UUID    getCourseId()              { return courseId; }

    /** @return identificador de la lección principal asociada */
    public UUID    getLessonId()              { return lessonId; }

    /** @return identificador del instructor que creó la regla */
    public UUID    getInstructorId()          { return instructorId; }

    /** @return umbral de puntaje en porcentaje (0-100) */
    public double  getScoreThresholdPct()     { return scoreThresholdPct; }

    /** @return identificador de la lección suplementaria de refuerzo */
    public UUID    getSupplementaryLessonId() { return supplementaryLessonId; }

    /** @return mensaje motivacional personalizado del instructor */
    public String  getMessage()               { return message; }

    /** @return {@code true} si la regla está activa */
    public boolean isActive()                 { return active; }

    /** @return instante de creación de la regla */
    public Instant getCreatedAt()             { return createdAt; }

    /** @return instante de última modificación */
    public Instant getUpdatedAt()             { return updatedAt; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant getDeletedAt()             { return deletedAt; }

    /**
     * Establece el identificador de la evaluación a la que aplica esta regla.
     *
     * @param a UUID de la evaluación
     */
    public void setAssessmentId(UUID a)              { this.assessmentId = a; }

    /**
     * Establece el identificador del curso al que pertenece la regla.
     *
     * @param c UUID del curso
     */
    public void setCourseId(UUID c)                  { this.courseId = c; }

    /**
     * Establece el identificador de la lección principal.
     *
     * @param l UUID de la lección
     */
    public void setLessonId(UUID l)                  { this.lessonId = l; }

    /**
     * Establece el identificador del instructor propietario de la regla.
     *
     * @param i UUID del instructor
     */
    public void setInstructorId(UUID i)              { this.instructorId = i; }

    /**
     * Establece el umbral de puntaje en porcentaje.
     *
     * @param t porcentaje umbral (0-100)
     */
    public void setScoreThresholdPct(double t)       { this.scoreThresholdPct = t; }

    /**
     * Establece la lección suplementaria de refuerzo.
     *
     * @param s UUID de la lección suplementaria
     */
    public void setSupplementaryLessonId(UUID s)     { this.supplementaryLessonId = s; }

    /**
     * Establece el mensaje motivacional del instructor.
     *
     * @param message texto del mensaje (máx. 500 caracteres)
     */
    public void setMessage(String message)           { this.message = message; }

    /**
     * Activa o desactiva la regla adaptativa.
     *
     * @param active {@code true} para activar, {@code false} para desactivar
     */
    public void setActive(boolean active)            { this.active = active; }
}
