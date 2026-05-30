package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hilo de discusión dentro de un foro de la plataforma.
 *
 * <p>Cada hilo tiene un primer post creado automáticamente con el contenido
 * inicial del autor. Los moderadores (instructor/admin) pueden fijar ({@code pinned})
 * o bloquear ({@code locked}) hilos. Los hilos bloqueados no aceptan nuevas
 * respuestas. El borrado es lógico.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "threads", schema = "collaboration")
public class Thread {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forum_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_thread_forum"))
    private Forum forum;

    @NotBlank
    @Column(name = "title", nullable = false, length = 300)
    private String title;

    /** Identificador del usuario que abrió el hilo. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** {@code true} si el hilo aparece fijado al tope del listado del foro. */
    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    /** {@code true} si el hilo está bloqueado y no acepta nuevas respuestas. */
    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    @OneToMany(mappedBy = "thread",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Post> posts = new ArrayList<>();

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
     * Indica si el hilo ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca el hilo como eliminado de forma lógica.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único del hilo */
    public UUID       getId()        { return id; }

    /** @return foro al que pertenece el hilo */
    public Forum      getForum()     { return forum; }

    /** @return título del hilo */
    public String     getTitle()     { return title; }

    /** @return identificador del autor que abrió el hilo */
    public UUID       getAuthorId()  { return authorId; }

    /** @return {@code true} si el hilo está fijado al tope del foro */
    public boolean    isPinned()     { return pinned; }

    /** @return {@code true} si el hilo está bloqueado para nuevas respuestas */
    public boolean    isLocked()     { return locked; }

    /** @return lista de posts del hilo, ordenados por fecha de creación ascendente */
    public List<Post> getPosts()     { return posts; }

    /** @return instante de creación del hilo */
    public Instant    getCreatedAt() { return createdAt; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant    getDeletedAt() { return deletedAt; }

    /**
     * Establece el foro propietario del hilo.
     *
     * @param forum foro al que pertenece
     */
    public void setForum(Forum forum)  { this.forum = forum; }

    /**
     * Establece el título del hilo.
     *
     * @param title título no nulo ni en blanco (máx. 300 caracteres)
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * Establece el identificador del autor del hilo.
     *
     * @param a UUID del usuario que abrió el hilo
     */
    public void setAuthorId(UUID a)    { this.authorId = a; }

    /**
     * Fija o desfija el hilo al tope del listado del foro.
     *
     * @param p {@code true} para fijar el hilo
     */
    public void setPinned(boolean p)   { this.pinned = p; }

    /**
     * Bloquea o desbloquea el hilo para nuevas respuestas.
     *
     * @param l {@code true} para bloquear el hilo
     */
    public void setLocked(boolean l)   { this.locked = l; }
}
