package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta o contribución publicada en un hilo de foro.
 *
 * <p>El primer post de un hilo se crea automáticamente cuando se crea el hilo.
 * Los posts se ordenan por fecha de creación ascendente para presentar la
 * conversación en orden cronológico. El borrado es lógico.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "posts", schema = "collaboration")
public class Post {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_post_thread"))
    private Thread thread;

    /** Identificador del usuario que publicó el post. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** {@code true} si el contenido del post ha sido editado tras la publicación. */
    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Genera el UUID y establece las marcas de tiempo al persistir por primera vez.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Actualiza la marca de tiempo de modificación en cada actualización.
     */
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /**
     * Indica si el post ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca el post como eliminado de forma lógica.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único del post */
    public UUID    getId()        { return id; }

    /** @return hilo al que pertenece el post */
    public Thread  getThread()    { return thread; }

    /** @return identificador del autor del post */
    public UUID    getAuthorId()  { return authorId; }

    /** @return contenido textual del post */
    public String  getContent()   { return content; }

    /** @return {@code true} si el post fue editado tras la publicación */
    public boolean isEdited()     { return edited; }

    /** @return instante de creación del post */
    public Instant getCreatedAt() { return createdAt; }

    /** @return instante de última modificación */
    public Instant getUpdatedAt() { return updatedAt; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant getDeletedAt() { return deletedAt; }

    /**
     * Establece el hilo al que pertenece el post.
     *
     * @param thread hilo propietario
     */
    public void setThread(Thread thread)   { this.thread = thread; }

    /**
     * Establece el identificador del autor del post.
     *
     * @param authorId UUID del usuario autor
     */
    public void setAuthorId(UUID authorId) { this.authorId = authorId; }

    /**
     * Establece el contenido textual del post.
     *
     * @param content texto del post (no debe estar en blanco)
     */
    public void setContent(String content) { this.content = content; }

    /**
     * Marca o desmarca el post como editado.
     *
     * @param edited {@code true} si el post fue modificado tras la publicación
     */
    public void setEdited(boolean edited)  { this.edited = edited; }
}
