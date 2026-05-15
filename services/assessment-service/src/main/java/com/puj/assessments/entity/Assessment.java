package com.puj.assessments.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assessments", schema = "assessments")
public class Assessment {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @NotNull
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "passing_score_pct", nullable = false)
    private double passingScorePct = 60.0;

    @Column(name = "randomize_questions", nullable = false)
    private boolean randomizeQuestions = false;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Question> questions = new ArrayList<>();

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

    public UUID           getId()                { return id; }
    public String         getTitle()             { return title; }
    public UUID           getCourseId()          { return courseId; }
    public UUID           getLessonId()          { return lessonId; }
    public UUID           getInstructorId()      { return instructorId; }
    public String         getDescription()       { return description; }
    public int            getMaxAttempts()       { return maxAttempts; }
    public Integer        getTimeLimitMinutes()  { return timeLimitMinutes; }
    public double         getPassingScorePct()   { return passingScorePct; }
    public boolean        isRandomizeQuestions() { return randomizeQuestions; }
    public List<Question> getQuestions()         { return questions; }
    public Instant        getCreatedAt()         { return createdAt; }
    public Instant        getUpdatedAt()         { return updatedAt; }
    public Instant        getDeletedAt()         { return deletedAt; }

    public void setTitle(String title)                       { this.title = title; }
    public void setCourseId(UUID courseId)                   { this.courseId = courseId; }
    public void setLessonId(UUID lessonId)                   { this.lessonId = lessonId; }
    public void setInstructorId(UUID instructorId)           { this.instructorId = instructorId; }
    public void setDescription(String description)           { this.description = description; }
    public void setMaxAttempts(int maxAttempts)              { this.maxAttempts = maxAttempts; }
    public void setTimeLimitMinutes(Integer t)               { this.timeLimitMinutes = t; }
    public void setPassingScorePct(double passingScorePct)   { this.passingScorePct = passingScorePct; }
    public void setRandomizeQuestions(boolean r)             { this.randomizeQuestions = r; }
}
