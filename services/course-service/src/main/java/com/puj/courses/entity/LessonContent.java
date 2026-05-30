package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa un bloque de contenido adicional dentro de una {@link Lesson lección}.
 *
 * <p>Permite estructurar el material didáctico de una lección en múltiples secciones
 * con distintos tipos (texto, vídeo, PDF, etc.) ordenadas por {@code orderIndex}.
 * A diferencia de {@link S3Resource}, esta entidad almacena contenido conceptual
 * y no necesariamente archivos binarios.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "lesson_contents", schema = "courses")
public class LessonContent {

    /** Identificador único del bloque de contenido, generado al persistir. */
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Lección a la que pertenece este bloque de contenido. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    /** Título del bloque de contenido; obligatorio, máximo 200 caracteres. */
    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    /** Descripción o texto del bloque en formato libre; puede ser {@code null}. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Tipo del bloque de contenido.
     * Valor por defecto: {@code "TEXT"}.
     */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType = "TEXT";

    /** URL del recurso asociado al bloque (vídeo, PDF, etc.); puede ser {@code null}. */
    @Column(name = "content_url", columnDefinition = "TEXT")
    private String contentUrl;

    /** Posición del bloque dentro de la lección; valores más bajos aparecen primero. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del bloque de contenido */
    public UUID    getId()          { return id; }

    /** @return lección padre del bloque */
    public Lesson  getLesson()      { return lesson; }

    /** @return título del bloque de contenido */
    public String  getTitle()       { return title; }

    /** @return descripción o texto del bloque, puede ser {@code null} */
    public String  getDescription() { return description; }

    /** @return tipo del bloque (TEXT, VIDEO, PDF, etc.) */
    public String  getContentType() { return contentType; }

    /** @return URL del recurso asociado, puede ser {@code null} */
    public String  getContentUrl()  { return contentUrl; }

    /** @return posición del bloque dentro de la lección */
    public int     getOrderIndex()  { return orderIndex; }

    /** @return marca temporal de creación */
    public Instant getCreatedAt()   { return createdAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna la lección padre de este bloque de contenido.
     *
     * @param l lección a la que pertenece el bloque; no debe ser {@code null}
     */
    public void setLesson(Lesson l)      { this.lesson = l; }

    /**
     * Establece el título del bloque de contenido.
     *
     * @param t nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void setTitle(String t)       { this.title = t; }

    /**
     * Establece la descripción o texto del bloque.
     *
     * @param d texto en formato libre; puede ser {@code null}
     */
    public void setDescription(String d) { this.description = d; }

    /**
     * Establece el tipo del bloque de contenido.
     * Si {@code t} es {@code null}, se asigna el valor por defecto {@code "TEXT"}.
     *
     * @param t tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT)
     */
    public void setContentType(String t) { this.contentType = t != null ? t : "TEXT"; }

    /**
     * Establece la URL del recurso asociado al bloque.
     *
     * @param u URL del recurso externo o S3; puede ser {@code null}
     */
    public void setContentUrl(String u)  { this.contentUrl = u; }

    /**
     * Establece la posición del bloque dentro de la lección.
     *
     * @param i índice de orden; valores más bajos se muestran primero
     */
    public void setOrderIndex(int i)     { this.orderIndex = i; }
}
