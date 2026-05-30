package com.puj.evaluaciones.evaluaciones.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad principal que representa una evaluación de la plataforma.
 *
 * <p>Una evaluación pertenece a un curso y, opcionalmente, a una lección.
 * Contiene la lista de preguntas activas, el umbral de aprobación y la
 * configuración de intentos y tiempo límite.</p>
 *
 * <p>El borrado es lógico (soft-delete): la evaluación se marca con
 * {@code deleted_at} y se excluye de las consultas activas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "assessments", schema = "assessments")
public class Evaluacion {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String titulo;

    @NotNull
    @Column(name = "course_id", nullable = false)
    private UUID idCurso;

    @Column(name = "lesson_id")
    private UUID idLeccion;

    @NotNull
    @Column(name = "instructor_id", nullable = false)
    private UUID idInstructor;

    @Column(name = "description", columnDefinition = "TEXT")
    private String descripcion;

    /** Número máximo de intentos permitidos por estudiante. Por defecto 3. */
    @Column(name = "max_attempts", nullable = false)
    private int maxIntentos = 3;

    /** Tiempo límite en minutos. {@code null} indica sin límite de tiempo. */
    @Column(name = "time_limit_minutes")
    private Integer limiteMinutos;

    /** Porcentaje mínimo de aciertos para aprobar la evaluación (0-100). Por defecto 60. */
    @Column(name = "passing_score_pct", nullable = false)
    private double porcentajeAprobacion = 60.0;

    /** Si {@code true}, el orden de las preguntas se aleatoriza para cada intento. */
    @Column(name = "randomize_questions", nullable = false)
    private boolean aleatorizarPreguntas = false;

    @OneToMany(mappedBy = "evaluacion",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<Pregunta> preguntas = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    /**
     * Genera el UUID y establece las marcas de tiempo al persistir por primera vez.
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
     * Indica si la evaluación ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean isEliminada() { return eliminadoEn != null; }

    /**
     * Marca la evaluación como eliminada de forma lógica con la marca de tiempo actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único de la evaluación */
    public UUID           getId()                   { return id; }

    /** @return título de la evaluación */
    public String         getTitulo()               { return titulo; }

    /** @return identificador del curso al que pertenece */
    public UUID           getIdCurso()              { return idCurso; }

    /** @return identificador de la lección asociada, o {@code null} */
    public UUID           getIdLeccion()            { return idLeccion; }

    /** @return identificador del instructor propietario */
    public UUID           getIdInstructor()         { return idInstructor; }

    /** @return descripción o instrucciones de la evaluación */
    public String         getDescripcion()          { return descripcion; }

    /** @return número máximo de intentos permitidos */
    public int            getMaxIntentos()          { return maxIntentos; }

    /** @return límite de tiempo en minutos, o {@code null} si no hay límite */
    public Integer        getLimiteMinutos()        { return limiteMinutos; }

    /** @return porcentaje mínimo para aprobar (0-100) */
    public double         getPorcentajeAprobacion() { return porcentajeAprobacion; }

    /** @return {@code true} si el orden de preguntas se aleatoriza */
    public boolean        isAleatorizarPreguntas()  { return aleatorizarPreguntas; }

    /** @return lista de preguntas ordenadas por {@code orden} */
    public List<Pregunta> getPreguntas()            { return preguntas; }

    /** @return instante de creación */
    public Instant        getCreadoEn()             { return creadoEn; }

    /** @return instante de última modificación */
    public Instant        getActualizadoEn()        { return actualizadoEn; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant        getEliminadoEn()          { return eliminadoEn; }

    /**
     * Establece el título de la evaluación.
     *
     * @param titulo título no nulo ni en blanco (máx. 200 caracteres)
     */
    public void setTitulo(String titulo)                        { this.titulo = titulo; }

    /**
     * Establece el curso al que pertenece la evaluación.
     *
     * @param idCurso UUID del curso
     */
    public void setIdCurso(UUID idCurso)                        { this.idCurso = idCurso; }

    /**
     * Establece la lección asociada a la evaluación.
     *
     * @param idLeccion UUID de la lección, o {@code null}
     */
    public void setIdLeccion(UUID idLeccion)                    { this.idLeccion = idLeccion; }

    /**
     * Establece el instructor propietario de la evaluación.
     *
     * @param idInstructor UUID del instructor
     */
    public void setIdInstructor(UUID idInstructor)              { this.idInstructor = idInstructor; }

    /**
     * Establece la descripción o instrucciones de la evaluación.
     *
     * @param descripcion texto libre sin límite de longitud
     */
    public void setDescripcion(String descripcion)              { this.descripcion = descripcion; }

    /**
     * Establece el número máximo de intentos permitidos por estudiante.
     *
     * @param maxIntentos número entero positivo de intentos
     */
    public void setMaxIntentos(int maxIntentos)                 { this.maxIntentos = maxIntentos; }

    /**
     * Establece el tiempo límite en minutos para completar la evaluación.
     *
     * @param limiteMinutos minutos de tiempo límite, o {@code null} para sin límite
     */
    public void setLimiteMinutos(Integer limiteMinutos)         { this.limiteMinutos = limiteMinutos; }

    /**
     * Establece el porcentaje mínimo de aciertos para aprobar.
     *
     * @param porcentajeAprobacion porcentaje (0-100)
     */
    public void setPorcentajeAprobacion(double porcentajeAprobacion) {
        this.porcentajeAprobacion = porcentajeAprobacion;
    }

    /**
     * Activa o desactiva la aleatorización del orden de las preguntas.
     *
     * @param aleatorizarPreguntas {@code true} para aleatorizar
     */
    public void setAleatorizarPreguntas(boolean aleatorizarPreguntas) {
        this.aleatorizarPreguntas = aleatorizarPreguntas;
    }
}
