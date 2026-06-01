package com.puj.cursos.modulos.dominio;

import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.lecciones.dominio.Leccion;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa un módulo dentro de un {@link Curso curso}.
 *
 * <p>Los módulos agrupan lecciones temáticamente y determinan el orden de navegación
 * del estudiante mediante {@code ordenIndex}. El borrado lógico se implementa a través
 * del campo {@code eliminadoEn}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "modules", schema = "courses")
public class Modulo {

    /** Identificador único del módulo, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Curso al que pertenece este módulo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "course_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_module_course"))
    private Curso curso;

    /** Título del módulo; obligatorio, máximo 200 caracteres. */
    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String titulo;

    /** Descripción del módulo en texto libre; puede ser {@code null}. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String descripcion;

    /** Posición del módulo dentro del curso; valores más bajos aparecen primero. */
    @Column(name = "order_index", nullable = false)
    private int ordenIndex;

    /** Lecciones del módulo ordenadas ascendentemente por {@code ordenIndex}. */
    @OneToMany(
            mappedBy      = "modulo",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    @OrderBy("ordenIndex ASC")
    private List<Leccion> lecciones = new ArrayList<>();

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Marca temporal de borrado lógico; {@code null} si el módulo está activo. */
    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code creadoEn} antes de la primera persistencia.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        creadoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si el módulo ha sido borrado lógicamente.
     *
     * @return {@code true} si {@code eliminadoEn} tiene un valor distinto de {@code null}
     */
    public boolean estaEliminado() { return eliminadoEn != null; }

    /**
     * Realiza el borrado lógico del módulo asignando la marca temporal actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del módulo */
    public UUID          getId()               { return id; }

    /** @return curso padre del módulo */
    public Curso         obtenerCurso()        { return curso; }

    /** @return título del módulo */
    public String        obtenerTitulo()       { return titulo; }

    /** @return descripción del módulo, puede ser {@code null} */
    public String        obtenerDescripcion()  { return descripcion; }

    /** @return posición del módulo dentro del curso */
    public int           obtenerOrdenIndex()   { return ordenIndex; }

    /** @return lista de lecciones del módulo */
    public List<Leccion> obtenerLecciones()    { return lecciones; }

    /** @return marca temporal de creación */
    public Instant       obtenerCreadoEn()     { return creadoEn; }

    /** @return marca temporal de borrado lógico, {@code null} si activo */
    public Instant       obtenerEliminadoEn()  { return eliminadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna el curso padre de este módulo.
     *
     * @param curso curso al que pertenece el módulo; no debe ser {@code null}
     */
    public void establecerCurso(Curso curso)              { this.curso = curso; }

    /**
     * Establece el título del módulo.
     *
     * @param titulo nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void establecerTitulo(String titulo)           { this.titulo = titulo; }

    /**
     * Establece la descripción del módulo.
     *
     * @param descripcion descripción en texto libre; puede ser {@code null}
     */
    public void establecerDescripcion(String descripcion) { this.descripcion = descripcion; }

    /**
     * Establece la posición del módulo dentro del curso.
     *
     * @param ordenIndex índice de orden; valores más bajos se muestran primero
     */
    public void establecerOrdenIndex(int ordenIndex)      { this.ordenIndex = ordenIndex; }
}
