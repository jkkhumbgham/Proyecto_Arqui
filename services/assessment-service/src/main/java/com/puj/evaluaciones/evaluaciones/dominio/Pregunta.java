package com.puj.evaluaciones.evaluaciones.dominio;

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
 * <p>Cada pregunta pertenece a exactamente una {@link Evaluacion} y puede
 * tener múltiples {@link OpcionRespuesta}s. El campo {@code order_index} controla
 * el orden de presentación. El borrado es lógico: la pregunta se marca con
 * {@code deleted_at} y se excluye del cálculo de puntaje.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "questions", schema = "assessments")
public class Pregunta {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_question_assessment"))
    private Evaluacion evaluacion;

    @NotBlank
    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String texto;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TipoPregunta tipo;

    /** Puntos asignados a esta pregunta. Por defecto 1.0. */
    @Column(name = "points", nullable = false)
    private double puntos = 1.0;

    /** Posición de la pregunta dentro de la evaluación (base 1). */
    @Column(name = "order_index", nullable = false)
    private int orden;

    @OneToMany(mappedBy = "pregunta",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<OpcionRespuesta> opciones = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    /**
     * Genera un UUID aleatorio antes de persistir la pregunta.
     */
    @PrePersist
    void alCrear() { if (id == null) id = UUID.randomUUID(); }

    /**
     * Indica si la pregunta ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean isEliminada() { return eliminadoEn != null; }

    /**
     * Marca la pregunta como eliminada de forma lógica con la marca de tiempo actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único de la pregunta */
    public UUID                    getId()         { return id; }

    /** @return evaluación propietaria de esta pregunta */
    public Evaluacion              getEvaluacion() { return evaluacion; }

    /** @return enunciado de la pregunta */
    public String                  getTexto()      { return texto; }

    /** @return tipo de la pregunta ({@link TipoPregunta}) */
    public TipoPregunta            getTipo()       { return tipo; }

    /** @return puntos asignados a esta pregunta */
    public double                  getPuntos()     { return puntos; }

    /** @return posición de la pregunta en la evaluación (base 1) */
    public int                     getOrden()      { return orden; }

    /** @return lista de opciones de respuesta de la pregunta */
    public List<OpcionRespuesta>   getOpciones()   { return opciones; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant                 getEliminadoEn(){ return eliminadoEn; }

    /**
     * Establece la evaluación propietaria de la pregunta.
     *
     * @param e evaluación a la que pertenece
     */
    public void setEvaluacion(Evaluacion e) { this.evaluacion = e; }

    /**
     * Establece el enunciado de la pregunta.
     *
     * @param texto texto del enunciado (no debe estar en blanco)
     */
    public void setTexto(String texto)      { this.texto = texto; }

    /**
     * Establece el tipo de pregunta.
     *
     * @param tipo uno de los valores de {@link TipoPregunta}
     */
    public void setTipo(TipoPregunta tipo)  { this.tipo = tipo; }

    /**
     * Establece los puntos asignados a la pregunta.
     *
     * @param puntos valor positivo de puntos
     */
    public void setPuntos(double puntos)    { this.puntos = puntos; }

    /**
     * Establece la posición de la pregunta en la evaluación.
     *
     * @param orden índice de orden (base 1)
     */
    public void setOrden(int orden)         { this.orden = orden; }
}
