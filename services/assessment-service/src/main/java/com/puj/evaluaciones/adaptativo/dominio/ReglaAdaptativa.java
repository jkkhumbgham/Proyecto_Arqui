package com.puj.evaluaciones.adaptativo.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Regla adaptativa determinística asociada a una evaluación.
 *
 * <p>Define el umbral de puntaje a partir del cual el motor adaptativo
 * redirige al estudiante hacia contenido suplementario. Cada regla tiene
 * restricción única por par {@code (assessment_id, course_id)}.</p>
 *
 * <p>Si el puntaje del estudiante cae por debajo de {@code score_threshold_pct},
 * se genera una {@link RecomendacionAdaptativa} apuntando a {@code supplementary_lesson_id}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "adaptive_rules", schema = "assessments",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_adaptive_rule",
                columnNames = {"assessment_id", "course_id"}))
public class ReglaAdaptativa {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID idEvaluacion;

    @Column(name = "course_id", nullable = false)
    private UUID idCurso;

    @Column(name = "lesson_id")
    private UUID idLeccion;

    @Column(name = "instructor_id", nullable = false)
    private UUID idInstructor;

    /** Umbral en porcentaje (0-100). Si {@code puntuacion < umbral} → contenido suplementario. */
    @Column(name = "score_threshold_pct", nullable = false)
    private double umbralPorcentaje;

    /** Lección de refuerzo a la que se redirige cuando el estudiante no supera el umbral. */
    @Column(name = "supplementary_lesson_id")
    private UUID idLeccionSupplementaria;

    /** Mensaje personalizado del instructor para motivar al estudiante. */
    @Column(name = "message", length = 500)
    private String mensaje;

    /** Indica si la regla está activa y debe evaluarse. */
    @Column(name = "active", nullable = false)
    private boolean activa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    /**
     * Inicializa el ID y las marcas de tiempo al persistir por primera vez.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        Instant ahora = Instant.now();
        creadoEn      = ahora;
        actualizadoEn = ahora;
    }

    /**
     * Actualiza la marca de tiempo de modificación en cada actualización.
     */
    @PreUpdate
    void alActualizar() { actualizadoEn = Instant.now(); }

    /**
     * Indica si la regla ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean isEliminada() { return eliminadoEn != null; }

    /**
     * Marca la regla como eliminada de forma lógica con la marca de tiempo actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único de la regla */
    public UUID    getId()                      { return id; }

    /** @return identificador de la evaluación a la que aplica esta regla */
    public UUID    getIdEvaluacion()            { return idEvaluacion; }

    /** @return identificador del curso al que pertenece la regla */
    public UUID    getIdCurso()                 { return idCurso; }

    /** @return identificador de la lección principal asociada */
    public UUID    getIdLeccion()               { return idLeccion; }

    /** @return identificador del instructor que creó la regla */
    public UUID    getIdInstructor()            { return idInstructor; }

    /** @return umbral de puntaje en porcentaje (0-100) */
    public double  getUmbralPorcentaje()        { return umbralPorcentaje; }

    /** @return identificador de la lección suplementaria de refuerzo */
    public UUID    getIdLeccionSupplementaria() { return idLeccionSupplementaria; }

    /** @return mensaje motivacional personalizado del instructor */
    public String  getMensaje()                 { return mensaje; }

    /** @return {@code true} si la regla está activa */
    public boolean isActiva()                   { return activa; }

    /** @return instante de creación de la regla */
    public Instant getCreadoEn()                { return creadoEn; }

    /** @return instante de última modificación */
    public Instant getActualizadoEn()           { return actualizadoEn; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant getEliminadoEn()             { return eliminadoEn; }

    /**
     * Establece el identificador de la evaluación a la que aplica esta regla.
     *
     * @param idEvaluacion UUID de la evaluación
     */
    public void setIdEvaluacion(UUID idEvaluacion)    { this.idEvaluacion = idEvaluacion; }

    /**
     * Establece el identificador del curso al que pertenece la regla.
     *
     * @param idCurso UUID del curso
     */
    public void setIdCurso(UUID idCurso)              { this.idCurso = idCurso; }

    /**
     * Establece el identificador de la lección principal.
     *
     * @param idLeccion UUID de la lección
     */
    public void setIdLeccion(UUID idLeccion)          { this.idLeccion = idLeccion; }

    /**
     * Establece el identificador del instructor propietario de la regla.
     *
     * @param idInstructor UUID del instructor
     */
    public void setIdInstructor(UUID idInstructor)    { this.idInstructor = idInstructor; }

    /**
     * Establece el umbral de puntaje en porcentaje.
     *
     * @param umbralPorcentaje porcentaje umbral (0-100)
     */
    public void setUmbralPorcentaje(double umbralPorcentaje) {
        this.umbralPorcentaje = umbralPorcentaje;
    }

    /**
     * Establece la lección suplementaria de refuerzo.
     *
     * @param idLeccionSupplementaria UUID de la lección suplementaria
     */
    public void setIdLeccionSupplementaria(UUID idLeccionSupplementaria) {
        this.idLeccionSupplementaria = idLeccionSupplementaria;
    }

    /**
     * Establece el mensaje motivacional del instructor.
     *
     * @param mensaje texto del mensaje (máx. 500 caracteres)
     */
    public void setMensaje(String mensaje)            { this.mensaje = mensaje; }

    /**
     * Activa o desactiva la regla adaptativa.
     *
     * @param activa {@code true} para activar, {@code false} para desactivar
     */
    public void setActiva(boolean activa)             { this.activa = activa; }
}
