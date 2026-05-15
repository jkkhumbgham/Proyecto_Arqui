package com.puj.assessments.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Regla adaptativa determinística: si el score del estudiante en la evaluación
 * de una lección cae por debajo del threshold, se redirige al contenido
 * suplementario indicado en supplementaryLessonId.
 */
@Entity
@Table(name = "adaptive_rules", schema = "assessments",
        uniqueConstraints = @UniqueConstraint(name = "uq_adaptive_rule",
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

    /** Umbral en porcentaje (0-100). Si score < threshold → contenido suplementario. */
    @Column(name = "score_threshold_pct", nullable = false)
    private double scoreThresholdPct;

    /** Lección de refuerzo a la que se redirige al estudiante cuando no supera el umbral. */
    @Column(name = "supplementary_lesson_id")
    private UUID supplementaryLessonId;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID    getId()                    { return id; }
    public UUID    getAssessmentId()          { return assessmentId; }
    public UUID    getCourseId()              { return courseId; }
    public UUID    getLessonId()              { return lessonId; }
    public UUID    getInstructorId()          { return instructorId; }
    public double  getScoreThresholdPct()     { return scoreThresholdPct; }
    public UUID    getSupplementaryLessonId() { return supplementaryLessonId; }
    public String  getMessage()               { return message; }
    public boolean isActive()                 { return active; }
    public Instant getCreatedAt()             { return createdAt; }
    public Instant getUpdatedAt()             { return updatedAt; }
    public Instant getDeletedAt()             { return deletedAt; }

    public void setAssessmentId(UUID a)              { this.assessmentId = a; }
    public void setCourseId(UUID c)                  { this.courseId = c; }
    public void setLessonId(UUID l)                  { this.lessonId = l; }
    public void setInstructorId(UUID i)              { this.instructorId = i; }
    public void setScoreThresholdPct(double t)       { this.scoreThresholdPct = t; }
    public void setSupplementaryLessonId(UUID s)     { this.supplementaryLessonId = s; }
    public void setMessage(String message)           { this.message = message; }
    public void setActive(boolean active)            { this.active = active; }
}
