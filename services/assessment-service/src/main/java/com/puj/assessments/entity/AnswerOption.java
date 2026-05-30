package com.puj.assessments.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Opción de respuesta de una pregunta de evaluación.
 *
 * <p>Cada {@link Question} puede tener múltiples opciones ordenadas por
 * {@code orderIndex}. El campo {@code correct} indica si la opción es la
 * respuesta válida; puede haber más de una correcta en preguntas de tipo
 * {@link QuestionType#MULTIPLE_CHOICE}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
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

    /** Posición de la opción dentro del listado de la pregunta (base 1). */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /**
     * Genera un UUID aleatorio para la opción antes de persistirla.
     */
    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }

    /** @return identificador único de la opción */
    public UUID     getId()         { return id; }

    /** @return pregunta propietaria de esta opción */
    public Question getQuestion()   { return question; }

    /** @return texto visible de la opción de respuesta */
    public String   getText()       { return text; }

    /** @return {@code true} si esta opción es una respuesta correcta */
    public boolean  isCorrect()     { return correct; }

    /** @return posición de ordenamiento de la opción (base 1) */
    public int      getOrderIndex() { return orderIndex; }

    /**
     * Establece la pregunta propietaria de esta opción.
     *
     * @param q pregunta a la que pertenece la opción
     */
    public void setQuestion(Question q)     { this.question = q; }

    /**
     * Establece el texto visible de la opción.
     *
     * @param text texto de la opción (no debe estar en blanco)
     */
    public void setText(String text)        { this.text = text; }

    /**
     * Marca la opción como correcta o incorrecta.
     *
     * @param correct {@code true} si la opción es una respuesta válida
     */
    public void setCorrect(boolean correct) { this.correct = correct; }

    /**
     * Establece la posición de la opción en el listado de la pregunta.
     *
     * @param idx índice de orden (base 1)
     */
    public void setOrderIndex(int idx)      { this.orderIndex = idx; }
}
