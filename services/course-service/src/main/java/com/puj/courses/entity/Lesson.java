package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa una lección dentro de un {@link Module módulo}.
 *
 * <p>Una lección puede contener texto, vídeo, PDF u otro tipo de recurso definido
 * por {@code contentType}. Las lecciones marcadas como suplementarias
 * ({@code supplementary = true}) no cuentan para el cálculo del progreso del curso.
 * El borrado lógico se realiza mediante {@link #softDelete()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "lessons", schema = "courses")
public class Lesson {

    /** Identificador único de la lección, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Módulo al que pertenece esta lección. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "module_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_lesson_module"))
    private Module module;

    /** Título de la lección; obligatorio, máximo 200 caracteres. */
    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Contenido textual de la lección en formato libre; puede ser {@code null}. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Posición de la lección dentro del módulo; valores más bajos aparecen primero. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** Duración estimada de la lección en minutos; puede ser {@code null}. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /**
     * Tipo de contenido principal de la lección.
     * Valores válidos: {@code TEXT}, {@code VIDEO}, {@code PDF}, {@code DOCUMENT}.
     */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType = "TEXT";

    /** URL del recurso externo o S3 asociado al tipo de contenido; puede ser {@code null}. */
    @Column(name = "content_url", columnDefinition = "TEXT")
    private String contentUrl;

    /**
     * Indica si la lección es material suplementario.
     * Las lecciones suplementarias no se contabilizan en el progreso del estudiante.
     */
    @Column(name = "is_supplementary", nullable = false)
    private boolean supplementary = false;

    /** Recursos S3 adjuntos a esta lección (archivos descargables). */
    @OneToMany(
            mappedBy      = "lesson",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    private List<S3Resource> resources = new ArrayList<>();

    /** Bloques de contenido adicionales de esta lección, ordenados por índice. */
    @OneToMany(
            mappedBy      = "lesson",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<LessonContent> contents = new ArrayList<>();

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Marca temporal de borrado lógico; {@code null} si la lección está activa. */
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
     * Indica si la lección ha sido borrada lógicamente.
     *
     * @return {@code true} si {@code deletedAt} tiene un valor distinto de {@code null}
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Realiza el borrado lógico de la lección asignando la marca temporal actual.
     */
    public void softDelete() { this.deletedAt = Instant.now(); }

    /**
     * Indica si la lección es material suplementario.
     *
     * @return {@code true} si la lección no cuenta para el progreso del curso
     */
    public boolean isSupplementary() { return supplementary; }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único de la lección */
    public UUID              getId()              { return id; }

    /** @return módulo padre de la lección */
    public Module            getModule()          { return module; }

    /** @return título de la lección */
    public String            getTitle()           { return title; }

    /** @return contenido textual de la lección, puede ser {@code null} */
    public String            getContent()         { return content; }

    /** @return posición de la lección dentro del módulo */
    public int               getOrderIndex()      { return orderIndex; }

    /** @return duración estimada en minutos, puede ser {@code null} */
    public Integer           getDurationMinutes() { return durationMinutes; }

    /** @return lista de recursos S3 adjuntos */
    public List<S3Resource>  getResources()       { return resources; }

    /** @return lista de bloques de contenido adicionales */
    public List<LessonContent> getContents()      { return contents; }

    /** @return marca temporal de creación */
    public Instant           getCreatedAt()       { return createdAt; }

    /** @return marca temporal de borrado lógico, {@code null} si activa */
    public Instant           getDeletedAt()       { return deletedAt; }

    /** @return tipo de contenido principal (TEXT, VIDEO, PDF, DOCUMENT) */
    public String            getContentType()     { return contentType; }

    /** @return URL del recurso externo o S3, puede ser {@code null} */
    public String            getContentUrl()      { return contentUrl; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna el módulo padre de esta lección.
     *
     * @param module módulo al que pertenece la lección; no debe ser {@code null}
     */
    public void setModule(Module module)          { this.module = module; }

    /**
     * Establece el título de la lección.
     *
     * @param title nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void setTitle(String title)            { this.title = title; }

    /**
     * Establece el contenido textual de la lección.
     *
     * @param content texto en formato libre; puede ser {@code null}
     */
    public void setContent(String content)        { this.content = content; }

    /**
     * Establece la posición de la lección dentro del módulo.
     *
     * @param orderIndex índice de orden; valores más bajos se muestran primero
     */
    public void setOrderIndex(int orderIndex)     { this.orderIndex = orderIndex; }

    /**
     * Establece la duración estimada de la lección.
     *
     * @param d duración en minutos; {@code null} indica sin duración definida
     */
    public void setDurationMinutes(Integer d)     { this.durationMinutes = d; }

    /**
     * Establece el tipo de contenido principal de la lección.
     *
     * @param t tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT)
     */
    public void setContentType(String t)          { this.contentType = t; }

    /**
     * Establece la URL del recurso externo o S3.
     *
     * @param u URL del recurso; puede ser {@code null}
     */
    public void setContentUrl(String u)           { this.contentUrl = u; }

    /**
     * Establece si la lección es material suplementario.
     *
     * @param s {@code true} para que la lección no cuente en el progreso del curso
     */
    public void setSupplementary(boolean s)       { this.supplementary = s; }
}
