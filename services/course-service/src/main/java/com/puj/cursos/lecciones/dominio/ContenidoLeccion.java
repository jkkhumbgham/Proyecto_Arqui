package com.puj.cursos.lecciones.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa un bloque de contenido adicional dentro de una {@link Leccion lección}.
 *
 * <p>Permite estructurar el material didáctico de una lección en múltiples secciones
 * con distintos tipos (texto, vídeo, PDF, etc.) ordenadas por {@code ordenIndex}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "lesson_contents", schema = "courses")
public class ContenidoLeccion {

    /** Identificador único del bloque de contenido, generado al persistir. */
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Lección a la que pertenece este bloque de contenido. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Leccion leccion;

    /** Título del bloque de contenido; obligatorio, máximo 200 caracteres. */
    @NotBlank
    @Column(nullable = false, length = 200)
    private String titulo;

    /** Descripción o texto del bloque en formato libre; puede ser {@code null}. */
    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /**
     * Tipo del bloque de contenido.
     * Valor por defecto: {@code "TEXT"}.
     */
    @Column(name = "content_type", nullable = false, length = 20)
    private String tipoContenido = "TEXT";

    /** URL del recurso asociado al bloque (vídeo, PDF, etc.); puede ser {@code null}. */
    @Column(name = "content_url", columnDefinition = "TEXT")
    private String urlContenido;

    /** Posición del bloque dentro de la lección; valores más bajos aparecen primero. */
    @Column(name = "order_index", nullable = false)
    private int ordenIndex = 0;

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

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
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del bloque de contenido */
    public UUID    getId()               { return id; }

    /** @return lección padre del bloque */
    public Leccion obtenerLeccion()      { return leccion; }

    /** @return título del bloque de contenido */
    public String  obtenerTitulo()       { return titulo; }

    /** @return descripción o texto del bloque, puede ser {@code null} */
    public String  obtenerDescripcion()  { return descripcion; }

    /** @return tipo del bloque (TEXT, VIDEO, PDF, etc.) */
    public String  obtenerTipoContenido() { return tipoContenido; }

    /** @return URL del recurso asociado, puede ser {@code null} */
    public String  obtenerUrlContenido() { return urlContenido; }

    /** @return posición del bloque dentro de la lección */
    public int     obtenerOrdenIndex()   { return ordenIndex; }

    /** @return marca temporal de creación */
    public Instant obtenerCreadoEn()     { return creadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna la lección padre de este bloque de contenido.
     *
     * @param l lección a la que pertenece el bloque; no debe ser {@code null}
     */
    public void establecerLeccion(Leccion l)      { this.leccion = l; }

    /**
     * Establece el título del bloque de contenido.
     *
     * @param t nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void establecerTitulo(String t)        { this.titulo = t; }

    /**
     * Establece la descripción o texto del bloque.
     *
     * @param d texto en formato libre; puede ser {@code null}
     */
    public void establecerDescripcion(String d)   { this.descripcion = d; }

    /**
     * Establece el tipo del bloque de contenido.
     * Si {@code t} es {@code null}, se asigna el valor por defecto {@code "TEXT"}.
     *
     * @param t tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT)
     */
    public void establecerTipoContenido(String t) { this.tipoContenido = t != null ? t : "TEXT"; }

    /**
     * Establece la URL del recurso asociado al bloque.
     *
     * @param u URL del recurso externo o S3; puede ser {@code null}
     */
    public void establecerUrlContenido(String u)  { this.urlContenido = u; }

    /**
     * Establece la posición del bloque dentro de la lección.
     *
     * @param i índice de orden; valores más bajos se muestran primero
     */
    public void establecerOrdenIndex(int i)       { this.ordenIndex = i; }
}
