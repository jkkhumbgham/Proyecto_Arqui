package com.puj.courses.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_progress", schema = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uq_progress_user_lesson",
                columnNames = {"user_id", "lesson_id"}))
public class LessonProgress {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_progress_lesson"))
    private Lesson lesson;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        completedAt = Instant.now();
    }

    public UUID    getId()          { return id; }
    public UUID    getUserId()      { return userId; }
    public Lesson  getLesson()      { return lesson; }
    public UUID    getCourseId()    { return courseId; }
    public Instant getCompletedAt() { return completedAt; }

    public void setUserId(UUID userId)    { this.userId = userId; }
    public void setLesson(Lesson lesson)  { this.lesson = lesson; }
    public void setCourseId(UUID courseId){ this.courseId = courseId; }
}
