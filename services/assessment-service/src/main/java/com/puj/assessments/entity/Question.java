package com.puj.assessments.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pregunta que forma parte de una evaluación.
 *
 * <p>Cada pregunta pertenece a exactamente una {@link Assessment} y puede
 * tener múltiples {@link AnswerOption}s. El campo {@code orderIndex} controla
 * el orden de presentación. El borrado es lógico: la pregunta se marca con
 * {@code deletedAt} y se excluye del cálculo de puntaje.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
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

    /** Puntos asignados a esta pregunta. Por defecto 1.0. */
    @Column(name = "points", nullable = false)
    private double points = 1.0;

    /** Posición de la pregunta dentro de la evaluación (base 1). */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "question",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<AnswerOption> options = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Genera un UUID aleatorio antes de persistir la pregunta.
     */
    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }

    /**
     * Indica si la pregunta ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca la pregunta como eliminada de forma lógica con la marca de tiempo actual.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único de la pregunta */
    public UUID               getId()         { return id; }

    /** @return evaluación propietaria de esta pregunta */
    public Assessment         getAssessment() { return assessment; }

    /** @return enunciado de la pregunta */
    public String             getText()       { return text; }

    /** @return tipo de la pregunta ({@link QuestionType}) */
    public QuestionType       getType()       { return type; }

    /** @return puntos asignados a esta pregunta */
    public double             getPoints()     { return points; }

    /** @return posición de la pregunta en la evaluación (base 1) */
    public int                getOrderIndex() { return orderIndex; }

    /** @return lista de opciones de respuesta de la pregunta */
    public List<AnswerOption> getOptions()    { return options; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant            getDeletedAt()  { return deletedAt; }

    /**
     * Establece la evaluación propietaria de la pregunta.
     *
     * @param a evaluación a la que pertenece
     */
    public void setAssessment(Assessment a)   { this.assessment = a; }

    /**
     * Establece el enunciado de la pregunta.
     *
     * @param text texto del enunciado (no debe estar en blanco)
     */
    public void setText(String text)          { this.text = text; }

    /**
     * Establece el tipo de pregunta.
     *
     * @param type uno de los valores de {@link QuestionType}
     */
    public void setType(QuestionType type)    { this.type = type; }

    /**
     * Establece los puntos asignados a la pregunta.
     *
     * @param points valor positivo de puntos
     */
    public void setPoints(double points)      { this.points = points; }

    /**
     * Establece la posición de la pregunta en la evaluación.
     *
     * @param orderIndex índice de orden (base 1)
     */
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
