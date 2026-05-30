package com.puj.colaboracion.foros.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta o contribución publicada en un hilo de foro.
 *
 * <p>La primera publicación de un hilo se crea automáticamente cuando se crea el hilo.
 * Las publicaciones se ordenan por fecha de creación ascendente para presentar la
 * conversación en orden cronológico. El borrado es lógico.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "posts", schema = "collaboration")
public class Publicacion {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_post_thread"))
    private Hilo hilo;

    /** Identificador del usuario que publicó la respuesta. */
    @Column(name = "author_id", nullable = false)
    private UUID idAutor;

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    /** {@code true} si el contenido fue editado tras la publicación. */
    @Column(name = "edited", nullable = false)
    private boolean editado = false;

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
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    /**
     * Actualiza la marca de tiempo de modificación en cada actualización.
     */
    @PreUpdate
    void alActualizar() { actualizadoEn = Instant.now(); }

    /**
     * Indica si la publicación ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean estaEliminada() { return eliminadoEn != null; }

    /**
     * Marca la publicación como eliminada de forma lógica.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único de la publicación */
    public UUID    getId()             { return id; }

    /** @return hilo al que pertenece la publicación */
    public Hilo    getHilo()           { return hilo; }

    /** @return identificador del autor de la publicación */
    public UUID    getIdAutor()        { return idAutor; }

    /** @return contenido textual de la publicación */
    public String  getContenido()      { return contenido; }

    /** @return {@code true} si la publicación fue editada tras la publicación */
    public boolean estaEditada()       { return editado; }

    /** @return instante de creación de la publicación */
    public Instant getCreadoEn()       { return creadoEn; }

    /** @return instante de última modificación */
    public Instant getActualizadoEn()  { return actualizadoEn; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant getEliminadoEn()    { return eliminadoEn; }

    /**
     * Establece el hilo al que pertenece la publicación.
     *
     * @param hilo hilo propietario
     */
    public void setHilo(Hilo hilo)           { this.hilo = hilo; }

    /**
     * Establece el identificador del autor de la publicación.
     *
     * @param idAutor UUID del usuario autor
     */
    public void setIdAutor(UUID idAutor)     { this.idAutor = idAutor; }

    /**
     * Establece el contenido textual de la publicación.
     *
     * @param contenido texto de la publicación (no debe estar en blanco)
     */
    public void setContenido(String contenido) { this.contenido = contenido; }

    /**
     * Marca o desmarca la publicación como editada.
     *
     * @param editado {@code true} si fue modificada tras la publicación
     */
    public void setEditado(boolean editado)  { this.editado = editado; }
}
