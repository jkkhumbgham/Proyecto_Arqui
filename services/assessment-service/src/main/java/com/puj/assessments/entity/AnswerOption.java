package com.puj.assessments.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "answer_options", schema = "assessments")
public class AnswerOption {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_option_question"))
    private Question question;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }

    public UUID     getId()         { return id; }
    public Question getQuestion()   { return question; }
    public String   getText()       { return text; }
    public boolean  isCorrect()     { return correct; }
    public int      getOrderIndex() { return orderIndex; }

    public void setQuestion(Question q)    { this.question = q; }
    public void setText(String text)       { this.text = text; }
    public void setCorrect(boolean correct){ this.correct = correct; }
    public void setOrderIndex(int idx)     { this.orderIndex = idx; }
}
