package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa un módulo dentro de un {@link Course curso}.
 *
 * <p>Los módulos agrupan lecciones temáticamente y determinan el orden de navegación
 * del estudiante mediante {@code orderIndex}. El borrado lógico se implementa a través
 * del campo {@code deletedAt}; los módulos eliminados son excluidos de las respuestas
 * al filtrar con {@link #isDeleted()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "modules", schema = "courses")
public class Module {

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
    private Course course;

    /** Título del módulo; obligatorio, máximo 200 caracteres. */
    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Descripción del módulo en texto libre; puede ser {@code null}. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Posición del módulo dentro del curso; valores más bajos aparecen primero. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** Lecciones del módulo ordenadas ascendentemente por {@code orderIndex}. */
    @OneToMany(
            mappedBy      = "module",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Lesson> lessons = new ArrayList<>();

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Marca temporal de borrado lógico; {@code null} si el módulo está activo. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code createdAt} antes de la primera persistencia.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si el módulo ha sido borrado lógicamente.
     *
     * @return {@code true} si {@code deletedAt} tiene un valor distinto de {@code null}
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Realiza el borrado lógico del módulo asignando la marca temporal actual.
     */
    public void softDelete() { this.deletedAt = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del módulo */
    public UUID         getId()          { return id; }

    /** @return curso padre del módulo */
    public Course       getCourse()      { return course; }

    /** @return título del módulo */
    public String       getTitle()       { return title; }

    /** @return descripción del módulo, puede ser {@code null} */
    public String       getDescription() { return description; }

    /** @return posición del módulo dentro del curso */
    public int          getOrderIndex()  { return orderIndex; }

    /** @return lista de lecciones del módulo */
    public List<Lesson> getLessons()     { return lessons; }

    /** @return marca temporal de creación */
    public Instant      getCreatedAt()   { return createdAt; }

    /** @return marca temporal de borrado lógico, {@code null} si activo */
    public Instant      getDeletedAt()   { return deletedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna el curso padre de este módulo.
     *
     * @param course curso al que pertenece el módulo; no debe ser {@code null}
     */
    public void setCourse(Course course)           { this.course = course; }

    /**
     * Establece el título del módulo.
     *
     * @param title nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void setTitle(String title)             { this.title = title; }

    /**
     * Establece la descripción del módulo.
     *
     * @param description descripción en texto libre; puede ser {@code null}
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * Establece la posición del módulo dentro del curso.
     *
     * @param orderIndex índice de orden; valores más bajos se muestran primero
     */
    public void setOrderIndex(int orderIndex)      { this.orderIndex = orderIndex; }
}
