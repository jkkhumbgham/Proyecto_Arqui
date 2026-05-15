package com.puj.assessments.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submissions", schema = "assessments")
public class Submission {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submission_assessment"))
    private Assessment assessment;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "passed", nullable = false)
    private boolean passed = false;

    @Column(name = "duration_seconds")
    private long durationSeconds;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        startedAt = Instant.now();
    }

    public boolean isDeleted() { return false; }

    public UUID             getId()             { return id; }
    public UUID             getUserId()         { return userId; }
    public Assessment       getAssessment()     { return assessment; }
    public BigDecimal       getScore()          { return score; }
    public BigDecimal       getMaxScore()       { return maxScore; }
    public boolean          isPassed()          { return passed; }
    public long             getDurationSeconds(){ return durationSeconds; }
    public int              getAttemptNumber()  { return attemptNumber; }
    public SubmissionStatus getStatus()         { return status; }
    public Instant          getStartedAt()      { return startedAt; }
    public Instant          getSubmittedAt()    { return submittedAt; }
    public Instant          getGradedAt()       { return gradedAt; }
    public String           getAnswersJson()    { return answersJson; }

    public void setUserId(UUID userId)                   { this.userId = userId; }
    public void setAssessment(Assessment a)              { this.assessment = a; }
    public void setScore(BigDecimal score)               { this.score = score; }
    public void setMaxScore(BigDecimal maxScore)         { this.maxScore = maxScore; }
    public void setPassed(boolean passed)                { this.passed = passed; }
    public void setDurationSeconds(long d)               { this.durationSeconds = d; }
    public void setAttemptNumber(int attemptNumber)      { this.attemptNumber = attemptNumber; }
    public void setStatus(SubmissionStatus status)       { this.status = status; }
    public void setSubmittedAt(Instant submittedAt)      { this.submittedAt = submittedAt; }
    public void setGradedAt(Instant gradedAt)            { this.gradedAt = gradedAt; }
    public void setAnswersJson(String answersJson)       { this.answersJson = answersJson; }
}
