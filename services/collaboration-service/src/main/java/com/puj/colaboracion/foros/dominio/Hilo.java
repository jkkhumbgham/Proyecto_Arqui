package com.puj.colaboracion.foros.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hilo de discusión dentro de un foro de la plataforma.
 *
 * <p>Cada hilo tiene una primera publicación creada automáticamente con el contenido
 * inicial del autor. Los moderadores (instructor/admin) pueden fijar ({@code pinned})
 * o bloquear ({@code locked}) hilos. Los hilos bloqueados no aceptan nuevas
 * respuestas. El borrado es lógico.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "threads", schema = "collaboration")
public class Hilo {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forum_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_thread_forum"))
    private Foro foro;

    @NotBlank
    @Column(name = "title", nullable = false, length = 300)
    private String titulo;

    /** Identificador del usuario que abrió el hilo. */
    @Column(name = "author_id", nullable = false)
    private UUID idAutor;

    /** {@code true} si el hilo aparece fijado al tope del listado del foro. */
    @Column(name = "pinned", nullable = false)
    private boolean fijado = false;

    /** {@code true} si el hilo está bloqueado y no acepta nuevas respuestas. */
    @Column(name = "locked", nullable = false)
    private boolean bloqueado = false;

    @OneToMany(mappedBy = "hilo",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("creadoEn ASC")
    private List<Publicacion> publicaciones = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    /**
     * Genera el UUID y establece la marca de tiempo de creación al persistir.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        creadoEn = Instant.now();
    }

    /**
     * Indica si el hilo ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean estaEliminado() { return eliminadoEn != null; }

    /**
     * Marca el hilo como eliminado de forma lógica.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único del hilo */
    public UUID               getId()           { return id; }

    /** @return foro al que pertenece el hilo */
    public Foro               getForo()          { return foro; }

    /** @return título del hilo */
    public String             getTitulo()        { return titulo; }

    /** @return identificador del autor que abrió el hilo */
    public UUID               getIdAutor()       { return idAutor; }

    /** @return {@code true} si el hilo está fijado al tope del foro */
    public boolean            estaFijado()       { return fijado; }

    /** @return {@code true} si el hilo está bloqueado para nuevas respuestas */
    public boolean            estaBloqueado()    { return bloqueado; }

    /** @return lista de publicaciones del hilo, ordenadas por fecha ascendente */
    public List<Publicacion>  getPublicaciones() { return publicaciones; }

    /** @return instante de creación del hilo */
    public Instant            getCreadoEn()      { return creadoEn; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant            getEliminadoEn()   { return eliminadoEn; }

    /**
     * Establece el foro propietario del hilo.
     *
     * @param foro foro al que pertenece
     */
    public void setForo(Foro foro)       { this.foro = foro; }

    /**
     * Establece el título del hilo.
     *
     * @param titulo título no nulo ni en blanco (máx. 300 caracteres)
     */
    public void setTitulo(String titulo) { this.titulo = titulo; }

    /**
     * Establece el identificador del autor del hilo.
     *
     * @param idAutor UUID del usuario que abrió el hilo
     */
    public void setIdAutor(UUID idAutor) { this.idAutor = idAutor; }

    /**
     * Fija o desfija el hilo al tope del listado del foro.
     *
     * @param fijado {@code true} para fijar el hilo
     */
    public void setFijado(boolean fijado)         { this.fijado = fijado; }

    /**
     * Bloquea o desbloquea el hilo para nuevas respuestas.
     *
     * @param bloqueado {@code true} para bloquear el hilo
     */
    public void setBloqueado(boolean bloqueado)   { this.bloqueado = bloqueado; }
}
