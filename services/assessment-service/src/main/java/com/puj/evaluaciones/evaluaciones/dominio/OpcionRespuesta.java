package com.puj.evaluaciones.evaluaciones.dominio;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Opción de respuesta de una pregunta de evaluación.
 *
 * <p>Cada {@link Pregunta} puede tener múltiples opciones ordenadas por
 * {@code order_index}. El campo {@code is_correct} indica si la opción es la
 * respuesta válida; puede haber más de una correcta en preguntas de tipo
 * {@link TipoPregunta#MULTIPLE_CHOICE}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "answer_options", schema = "assessments")
public class OpcionRespuesta {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_option_question"))
    private Pregunta pregunta;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "is_correct", nullable = false)
    private boolean correcta;

    /** Posición de la opción dentro del listado de la pregunta (base 1). */
    @Column(name = "order_index", nullable = false)
    private int orden;

    /**
     * Genera un UUID aleatorio para la opción antes de persistirla.
     */
    @PrePersist
    void alCrear() { if (id == null) id = UUID.randomUUID(); }

    /** @return identificador único de la opción */
    public UUID     getId()       { return id; }

    /** @return pregunta propietaria de esta opción */
    public Pregunta getPregunta() { return pregunta; }

    /** @return texto visible de la opción de respuesta */
    public String   getTexto()    { return texto; }

    /** @return {@code true} si esta opción es una respuesta correcta */
    public boolean  isCorrecta()  { return correcta; }

    /** @return posición de ordenamiento de la opción (base 1) */
    public int      getOrden()    { return orden; }

    /**
     * Establece la pregunta propietaria de esta opción.
     *
     * @param p pregunta a la que pertenece la opción
     */
    public void setPregunta(Pregunta p)       { this.pregunta = p; }

    /**
     * Establece el texto visible de la opción.
     *
     * @param texto texto de la opción (no debe estar en blanco)
     */
    public void setTexto(String texto)        { this.texto = texto; }

    /**
     * Marca la opción como correcta o incorrecta.
     *
     * @param correcta {@code true} si la opción es una respuesta válida
     */
    public void setCorrecta(boolean correcta) { this.correcta = correcta; }

    /**
     * Establece la posición de la opción en el listado de la pregunta.
     *
     * @param orden índice de orden (base 1)
     */
    public void setOrden(int orden)           { this.orden = orden; }
}
