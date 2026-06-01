package com.puj.cursos.lecciones.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que registra la finalización de una {@link Leccion lección} por parte de un usuario.
 *
 * <p>Existe una restricción de unicidad {@code (user_id, lesson_id)} que garantiza que
 * cada estudiante sólo puede completar una lección una vez. La marca temporal
 * {@code completadoEn} se establece automáticamente en {@link #alCrear()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(
        name              = "lesson_progress",
        schema            = "courses",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_progress_user_lesson",
                columnNames = {"user_id", "lesson_id"}))
public class ProgresoLeccion {

    /** Identificador único del registro de progreso, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identificador del usuario que completó la lección. */
    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    /** Lección que fue completada por el usuario. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "lesson_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_progress_lesson"))
    private Leccion leccion;

    /**
     * Identificador del curso al que pertenece la lección.
     * Desnormalizado para facilitar consultas de progreso por curso sin joins adicionales.
     */
    @Column(name = "course_id", nullable = false)
    private UUID idCurso;

    /** Marca temporal del momento en que se completó la lección; inmutable. */
    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completadoEn;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code completadoEn} antes de la primera persistencia.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        completadoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del registro de progreso */
    public UUID    getId()              { return id; }

    /** @return identificador del usuario que completó la lección */
    public UUID    obtenerIdUsuario()   { return idUsuario; }

    /** @return lección completada */
    public Leccion obtenerLeccion()     { return leccion; }

    /** @return identificador del curso al que pertenece la lección */
    public UUID    obtenerIdCurso()     { return idCurso; }

    /** @return marca temporal de finalización de la lección */
    public Instant obtenerCompletadoEn() { return completadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador del usuario que completó la lección.
     *
     * @param idUsuario UUID del estudiante; no debe ser {@code null}
     */
    public void establecerIdUsuario(UUID idUsuario)    { this.idUsuario = idUsuario; }

    /**
     * Asigna la lección completada.
     *
     * @param leccion lección finalizada por el usuario; no debe ser {@code null}
     */
    public void establecerLeccion(Leccion leccion)     { this.leccion = leccion; }

    /**
     * Establece el identificador del curso al que pertenece la lección.
     *
     * @param idCurso UUID del curso; no debe ser {@code null}
     */
    public void establecerIdCurso(UUID idCurso)        { this.idCurso = idCurso; }
}
