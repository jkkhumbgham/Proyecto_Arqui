package com.puj.assessments.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "questions", schema = "assessments")
public class Question {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_question_assessment"))
    private Assessment assessment;

    @NotBlank
    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private QuestionType type;

    @Column(name = "points", nullable = false)
    private double points = 1.0;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<AnswerOption> options = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID               getId()          { return id; }
    public Assessment         getAssessment()  { return assessment; }
    public String             getText()        { return text; }
    public QuestionType       getType()        { return type; }
    public double             getPoints()      { return points; }
    public int                getOrderIndex()  { return orderIndex; }
    public List<AnswerOption> getOptions()     { return options; }
    public Instant            getDeletedAt()   { return deletedAt; }

    public void setAssessment(Assessment a)    { this.assessment = a; }
    public void setText(String text)           { this.text = text; }
    public void setType(QuestionType type)     { this.type = type; }
    public void setPoints(double points)       { this.points = points; }
    public void setOrderIndex(int orderIndex)  { this.orderIndex = orderIndex; }
}
