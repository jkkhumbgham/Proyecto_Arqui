package com.puj.cursos.cursos.dominio;

import com.puj.cursos.modulos.dominio.Modulo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa un curso en la plataforma de aprendizaje.
 *
 * <p>Un curso pertenece a un instructor, contiene módulos ordenados y tiene un ciclo
 * de vida definido por {@link EstadoCurso}. El borrado lógico (soft delete) se realiza
 * asignando {@code eliminadoEn}; los registros eliminados son ignorados por la capa
 * de repositorio.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "courses", schema = "courses",
        indexes = { @Index(name = "ix_courses_instructor", columnList = "instructor_id") })
public class Curso {

    /** Identificador único del curso, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Título del curso; máximo 200 caracteres, obligatorio. */
    @NotBlank @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String titulo;

    /** Descripción detallada del curso en texto libre. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String descripcion;

    /** Identificador del usuario instructor propietario del curso. */
    @NotNull
    @Column(name = "instructor_id", nullable = false)
    private UUID idInstructor;

    /** Estado actual del ciclo de vida del curso; valor por defecto {@code DRAFT}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoCurso estado = EstadoCurso.DRAFT;

    /** URL de la imagen de portada almacenada en S3; puede ser {@code null}. */
    @Column(name = "cover_image_url", length = 500)
    private String urlImagenPortada;

    /** Número máximo de estudiantes permitidos; {@code null} indica sin límite. */
    @Column(name = "max_students")
    private Integer maxEstudiantes;

    /** Módulos del curso ordenados por {@code orderIndex} de forma ascendente. */
    @OneToMany(
            mappedBy      = "curso",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    @OrderBy("ordenIndex ASC")
    private List<Modulo> modulos = new ArrayList<>();

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Marca temporal de la última actualización. */
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    /** Categoría temática del curso (ej. "Programación", "Diseño"). */
    @Column(name = "category", length = 100)
    private String categoria;

    /** Marca temporal de borrado lógico; {@code null} si el curso está activo. */
    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    // -------------------------------------------------------------------------
    // Callbacks JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id}, {@code creadoEn} y {@code actualizadoEn} antes de persistir.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        Instant ahora = Instant.now();
        creadoEn      = ahora;
        actualizadoEn = ahora;
    }

    /**
     * Actualiza {@code actualizadoEn} en cada modificación de la entidad.
     */
    @PreUpdate
    void alActualizar() { actualizadoEn = Instant.now(); }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si el curso ha sido borrado lógicamente.
     *
     * @return {@code true} si {@code eliminadoEn} tiene un valor distinto de {@code null}
     */
    public boolean estaEliminado() { return eliminadoEn != null; }

    /**
     * Realiza el borrado lógico del curso asignando la marca temporal actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del curso */
    public UUID          getId()               { return id; }

    /** @return título del curso */
    public String        obtenerTitulo()       { return titulo; }

    /** @return descripción del curso, puede ser {@code null} */
    public String        obtenerDescripcion()  { return descripcion; }

    /** @return identificador del instructor propietario */
    public UUID          obtenerIdInstructor() { return idInstructor; }

    /** @return estado actual del curso */
    public EstadoCurso   obtenerEstado()       { return estado; }

    /** @return URL de la imagen de portada, puede ser {@code null} */
    public String        obtenerUrlImagenPortada() { return urlImagenPortada; }

    /** @return categoría temática del curso, puede ser {@code null} */
    public String        obtenerCategoria()    { return categoria; }

    /** @return capacidad máxima de estudiantes, puede ser {@code null} */
    public Integer       obtenerMaxEstudiantes() { return maxEstudiantes; }

    /** @return lista de módulos del curso */
    public List<Modulo>  obtenerModulos()      { return modulos; }

    /** @return marca temporal de creación */
    public Instant       obtenerCreadoEn()     { return creadoEn; }

    /** @return marca temporal de última actualización */
    public Instant       obtenerActualizadoEn() { return actualizadoEn; }

    /** @return marca temporal de borrado lógico, {@code null} si activo */
    public Instant       obtenerEliminadoEn()  { return eliminadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece el título del curso.
     *
     * @param titulo nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void establecerTitulo(String titulo)              { this.titulo = titulo; }

    /**
     * Establece la descripción del curso.
     *
     * @param descripcion descripción en texto libre; puede ser {@code null}
     */
    public void establecerDescripcion(String descripcion)    { this.descripcion = descripcion; }

    /**
     * Establece el identificador del instructor propietario.
     *
     * @param idInstructor UUID del usuario con rol INSTRUCTOR
     */
    public void establecerIdInstructor(UUID idInstructor)    { this.idInstructor = idInstructor; }

    /**
     * Establece el estado del ciclo de vida del curso.
     *
     * @param estado nuevo estado; no debe ser {@code null}
     */
    public void establecerEstado(EstadoCurso estado)         { this.estado = estado; }

    /**
     * Establece la URL de la imagen de portada.
     *
     * @param url URL de la imagen almacenada en S3; puede ser {@code null}
     */
    public void establecerUrlImagenPortada(String url)       { this.urlImagenPortada = url; }

    /**
     * Establece la categoría temática del curso.
     *
     * @param categoria nombre de la categoría; puede ser {@code null}
     */
    public void establecerCategoria(String categoria)        { this.categoria = categoria; }

    /**
     * Establece el número máximo de estudiantes permitidos.
     *
     * @param maxEstudiantes límite de inscripciones; {@code null} elimina el límite
     */
    public void establecerMaxEstudiantes(Integer maxEstudiantes) { this.maxEstudiantes = maxEstudiantes; }
}
