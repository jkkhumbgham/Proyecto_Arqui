package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Foro de discusión asociado a un curso de la plataforma.
 *
 * <p>Cada curso puede tener uno o más foros. Un foro agrupa hilos de
 * discusión ({@link Thread}) que a su vez contienen posts ({@link Post}).
 * El borrado es lógico: el foro se marca con {@code deletedAt} y se excluye
 * de las consultas activas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "forums", schema = "collaboration")
public class Forum {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Identificador del curso al que pertenece el foro. */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Identificador del usuario que creó el foro (instructor o admin). */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "forum",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<Thread> threads = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Genera el UUID y establece la marca de tiempo de creación al persistir.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    /**
     * Indica si el foro ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca el foro como eliminado de forma lógica con la marca de tiempo actual.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único del foro */
    public UUID         getId()          { return id; }

    /** @return título del foro */
    public String       getTitle()       { return title; }

    /** @return identificador del curso al que pertenece */
    public UUID         getCourseId()    { return courseId; }

    /** @return identificador del creador del foro */
    public UUID         getCreatedBy()   { return createdBy; }

    /** @return descripción del foro */
    public String       getDescription() { return description; }

    /** @return lista de hilos del foro, ordenados por fecha de creación descendente */
    public List<Thread> getThreads()     { return threads; }

    /** @return instante de creación del foro */
    public Instant      getCreatedAt()   { return createdAt; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant      getDeletedAt()   { return deletedAt; }

    /**
     * Establece el título del foro.
     *
     * @param title título no nulo ni en blanco (máx. 200 caracteres)
     */
    public void setTitle(String title)             { this.title = title; }

    /**
     * Establece el curso al que pertenece el foro.
     *
     * @param courseId UUID del curso
     */
    public void setCourseId(UUID courseId)         { this.courseId = courseId; }

    /**
     * Establece el creador del foro.
     *
     * @param createdBy UUID del usuario creador
     */
    public void setCreatedBy(UUID createdBy)       { this.createdBy = createdBy; }

    /**
     * Establece la descripción del foro.
     *
     * @param description texto libre descriptivo
     */
    public void setDescription(String description) { this.description = description; }
}
