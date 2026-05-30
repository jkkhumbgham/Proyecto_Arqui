package com.puj.courses.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa la inscripción de un estudiante en un {@link Course curso}.
 *
 * <p>La restricción {@code uq_enrollment_user_course} garantiza que un mismo estudiante
 * no puede inscribirse dos veces en el mismo curso. El campo {@code progressPct} almacena
 * el porcentaje de lecciones completadas y se actualiza cada vez que el estudiante
 * finaliza una lección. El borrado lógico se implementa mediante {@link #softDelete()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(
        name              = "enrollments",
        schema            = "courses",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_enrollment_user_course",
                columnNames = {"user_id", "course_id"}))
public class Enrollment {

    /** Identificador único de la inscripción, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identificador del estudiante inscrito. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Curso en el que está inscrito el estudiante. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "course_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_enrollment_course"))
    private Course course;

    /** Estado actual de la inscripción; valor por defecto {@code ACTIVE}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    /** Porcentaje de progreso del estudiante en el curso (0.0 – 100.0). */
    @Column(name = "progress_pct", nullable = false)
    private double progressPct = 0.0;

    /** Marca temporal del momento de inscripción; inmutable. */
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    /** Marca temporal de finalización del curso; {@code null} si no ha completado. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Marca temporal de borrado lógico; {@code null} si la inscripción está activa. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code enrolledAt} antes de la primera persistencia.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        enrolledAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si la inscripción ha sido borrada lógicamente.
     *
     * @return {@code true} si {@code deletedAt} tiene un valor distinto de {@code null}
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Realiza el borrado lógico de la inscripción asignando la marca temporal actual.
     */
    public void softDelete() { this.deletedAt = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único de la inscripción */
    public UUID             getId()          { return id; }

    /** @return identificador del estudiante inscrito */
    public UUID             getUserId()      { return userId; }

    /** @return curso en el que está inscrito el estudiante */
    public Course           getCourse()      { return course; }

    /** @return estado actual de la inscripción */
    public EnrollmentStatus getStatus()      { return status; }

    /** @return porcentaje de progreso del estudiante (0.0 – 100.0) */
    public double           getProgressPct() { return progressPct; }

    /** @return marca temporal del momento de inscripción */
    public Instant          getEnrolledAt()  { return enrolledAt; }

    /** @return marca temporal de finalización, {@code null} si no ha completado */
    public Instant          getCompletedAt() { return completedAt; }

    /** @return marca temporal de borrado lógico, {@code null} si activa */
    public Instant          getDeletedAt()   { return deletedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador del estudiante.
     *
     * @param userId UUID del estudiante; no debe ser {@code null}
     */
    public void setUserId(UUID userId)              { this.userId = userId; }

    /**
     * Asigna el curso de la inscripción.
     *
     * @param course curso al que se inscribe el estudiante; no debe ser {@code null}
     */
    public void setCourse(Course course)            { this.course = course; }

    /**
     * Establece el estado de la inscripción.
     *
     * @param status nuevo estado; no debe ser {@code null}
     */
    public void setStatus(EnrollmentStatus status)  { this.status = status; }

    /**
     * Actualiza el porcentaje de progreso del estudiante.
     *
     * @param progressPct nuevo porcentaje, debe estar entre 0.0 y 100.0
     */
    public void setProgressPct(double progressPct)  { this.progressPct = progressPct; }

    /**
     * Establece la marca temporal de finalización del curso.
     *
     * @param completedAt instante en que el estudiante completó el curso; puede ser {@code null}
     */
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
