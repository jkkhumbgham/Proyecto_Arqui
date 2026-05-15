package com.puj.courses.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments", schema = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uq_enrollment_user_course",
                columnNames = {"user_id", "course_id"}))
public class Enrollment {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollment_course"))
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "progress_pct", nullable = false)
    private double progressPct = 0.0;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        enrolledAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID             getId()          { return id; }
    public UUID             getUserId()      { return userId; }
    public Course           getCourse()      { return course; }
    public EnrollmentStatus getStatus()      { return status; }
    public double           getProgressPct() { return progressPct; }
    public Instant          getEnrolledAt()  { return enrolledAt; }
    public Instant          getCompletedAt() { return completedAt; }
    public Instant          getDeletedAt()   { return deletedAt; }

    public void setUserId(UUID userId)                   { this.userId = userId; }
    public void setCourse(Course course)                 { this.course = course; }
    public void setStatus(EnrollmentStatus status)       { this.status = status; }
    public void setProgressPct(double progressPct)       { this.progressPct = progressPct; }
    public void setCompletedAt(Instant completedAt)      { this.completedAt = completedAt; }
}
