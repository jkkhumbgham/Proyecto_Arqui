package com.puj.cursos.matriculas.dominio;

import com.puj.cursos.cursos.dominio.Curso;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa la matrícula de un estudiante en un {@link Curso curso}.
 *
 * <p>La restricción {@code uq_enrollment_user_course} garantiza que un mismo estudiante
 * no puede matricularse dos veces en el mismo curso. El campo {@code porcentajeProgreso}
 * almacena el porcentaje de lecciones completadas y se actualiza cada vez que el estudiante
 * finaliza una lección. El borrado lógico se implementa mediante {@link #eliminarLogicamente()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(
        name              = "enrollments",
        schema            = "courses",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_enrollment_user_course",
                columnNames = {"user_id", "course_id"}))
public class Matricula {

    /** Identificador único de la matrícula, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identificador del estudiante matriculado. */
    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    /** Curso en el que está matriculado el estudiante. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "course_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_enrollment_course"))
    private Curso curso;

    /** Estado actual de la matrícula; valor por defecto {@code ACTIVE}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoMatricula estado = EstadoMatricula.ACTIVE;

    /** Porcentaje de progreso del estudiante en el curso (0.0 – 100.0). */
    @Column(name = "progress_pct", nullable = false)
    private double porcentajeProgreso = 0.0;

    /** Marca temporal del momento de matriculación; inmutable. */
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant matriculadoEn;

    /** Marca temporal de finalización del curso; {@code null} si no ha completado. */
    @Column(name = "completed_at")
    private Instant completadoEn;

    /** Marca temporal de borrado lógico; {@code null} si la matrícula está activa. */
    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code matriculadoEn} antes de la primera persistencia.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        matriculadoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si la matrícula ha sido borrada lógicamente.
     *
     * @return {@code true} si {@code eliminadoEn} tiene un valor distinto de {@code null}
     */
    public boolean estaEliminada() { return eliminadoEn != null; }

    /**
     * Realiza el borrado lógico de la matrícula asignando la marca temporal actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único de la matrícula */
    public UUID           getId()                    { return id; }

    /** @return identificador del estudiante matriculado */
    public UUID           obtenerIdUsuario()         { return idUsuario; }

    /** @return curso en el que está matriculado el estudiante */
    public Curso          obtenerCurso()             { return curso; }

    /** @return estado actual de la matrícula */
    public EstadoMatricula obtenerEstado()           { return estado; }

    /** @return porcentaje de progreso del estudiante (0.0 – 100.0) */
    public double         obtenerPorcentajeProgreso() { return porcentajeProgreso; }

    /** @return marca temporal del momento de matriculación */
    public Instant        obtenerMatriculadoEn()     { return matriculadoEn; }

    /** @return marca temporal de finalización, {@code null} si no ha completado */
    public Instant        obtenerCompletadoEn()      { return completadoEn; }

    /** @return marca temporal de borrado lógico, {@code null} si activa */
    public Instant        obtenerEliminadoEn()       { return eliminadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece el identificador del estudiante.
     *
     * @param idUsuario UUID del estudiante; no debe ser {@code null}
     */
    public void establecerIdUsuario(UUID idUsuario)                 { this.idUsuario = idUsuario; }

    /**
     * Asigna el curso de la matrícula.
     *
     * @param curso curso al que se matricula el estudiante; no debe ser {@code null}
     */
    public void establecerCurso(Curso curso)                        { this.curso = curso; }

    /**
     * Establece el estado de la matrícula.
     *
     * @param estado nuevo estado; no debe ser {@code null}
     */
    public void establecerEstado(EstadoMatricula estado)            { this.estado = estado; }

    /**
     * Actualiza el porcentaje de progreso del estudiante.
     *
     * @param porcentajeProgreso nuevo porcentaje, debe estar entre 0.0 y 100.0
     */
    public void establecerPorcentajeProgreso(double porcentajeProgreso) { this.porcentajeProgreso = porcentajeProgreso; }

    /**
     * Establece la marca temporal de finalización del curso.
     *
     * @param completadoEn instante en que el estudiante completó el curso; puede ser {@code null}
     */
    public void establecerCompletadoEn(Instant completadoEn)        { this.completadoEn = completadoEn; }
}
