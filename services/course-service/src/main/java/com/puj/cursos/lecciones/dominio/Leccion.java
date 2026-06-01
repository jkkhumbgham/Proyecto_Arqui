package com.puj.cursos.lecciones.dominio;

import com.puj.cursos.cursos.dominio.RecursosS3;
import com.puj.cursos.modulos.dominio.Modulo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa una lección dentro de un {@link Modulo módulo}.
 *
 * <p>Una lección puede contener texto, vídeo, PDF u otro tipo de recurso definido
 * por {@code tipoContenido}. Las lecciones marcadas como suplementarias
 * ({@code esSuplementaria = true}) no cuentan para el cálculo del progreso del curso.
 * El borrado lógico se realiza mediante {@link #eliminarLogicamente()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "lessons", schema = "courses")
public class Leccion {

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
    private Modulo modulo;

    /** Título de la lección; obligatorio, máximo 200 caracteres. */
    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String titulo;

    /** Contenido textual de la lección en formato libre; puede ser {@code null}. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String contenido;

    /** Posición de la lección dentro del módulo; valores más bajos aparecen primero. */
    @Column(name = "order_index", nullable = false)
    private int ordenIndex;

    /** Duración estimada de la lección en minutos; puede ser {@code null}. */
    @Column(name = "duration_minutes")
    private Integer duracionMinutos;

    /**
     * Tipo de contenido principal de la lección.
     * Valores válidos: {@code TEXT}, {@code VIDEO}, {@code PDF}, {@code DOCUMENT}.
     */
    @Column(name = "content_type", nullable = false, length = 20)
    private String tipoContenido = "TEXT";

    /** URL del recurso externo o S3 asociado al tipo de contenido; puede ser {@code null}. */
    @Column(name = "content_url", columnDefinition = "TEXT")
    private String urlContenido;

    /**
     * Indica si la lección es material suplementario.
     * Las lecciones suplementarias no se contabilizan en el progreso del estudiante.
     */
    @Column(name = "is_supplementary", nullable = false)
    private boolean esSuplementaria = false;

    /** Recursos S3 adjuntos a esta lección (archivos descargables). */
    @OneToMany(
            mappedBy      = "leccion",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    private List<RecursosS3> recursos = new ArrayList<>();

    /** Bloques de contenido adicionales de esta lección, ordenados por índice. */
    @OneToMany(
            mappedBy      = "leccion",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY)
    @OrderBy("ordenIndex ASC")
    private List<ContenidoLeccion> contenidos = new ArrayList<>();

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Marca temporal de borrado lógico; {@code null} si la lección está activa. */
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
     * Indica si la lección ha sido borrada lógicamente.
     *
     * @return {@code true} si {@code eliminadoEn} tiene un valor distinto de {@code null}
     */
    public boolean estaEliminada() { return eliminadoEn != null; }

    /**
     * Realiza el borrado lógico de la lección asignando la marca temporal actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /**
     * Indica si la lección es material suplementario.
     *
     * @return {@code true} si la lección no cuenta para el progreso del curso
     */
    public boolean esSuplementaria() { return esSuplementaria; }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único de la lección */
    public UUID                  getId()                { return id; }

    /** @return módulo padre de la lección */
    public Modulo                obtenerModulo()        { return modulo; }

    /** @return título de la lección */
    public String                obtenerTitulo()        { return titulo; }

    /** @return contenido textual de la lección, puede ser {@code null} */
    public String                obtenerContenido()     { return contenido; }

    /** @return posición de la lección dentro del módulo */
    public int                   obtenerOrdenIndex()    { return ordenIndex; }

    /** @return duración estimada en minutos, puede ser {@code null} */
    public Integer               obtenerDuracionMinutos() { return duracionMinutos; }

    /** @return lista de recursos S3 adjuntos */
    public List<RecursosS3>      obtenerRecursos()      { return recursos; }

    /** @return lista de bloques de contenido adicionales */
    public List<ContenidoLeccion> obtenerContenidos()   { return contenidos; }

    /** @return marca temporal de creación */
    public Instant               obtenerCreadoEn()      { return creadoEn; }

    /** @return marca temporal de borrado lógico, {@code null} si activa */
    public Instant               obtenerEliminadoEn()   { return eliminadoEn; }

    /** @return tipo de contenido principal (TEXT, VIDEO, PDF, DOCUMENT) */
    public String                obtenerTipoContenido() { return tipoContenido; }

    /** @return URL del recurso externo o S3, puede ser {@code null} */
    public String                obtenerUrlContenido()  { return urlContenido; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna el módulo padre de esta lección.
     *
     * @param modulo módulo al que pertenece la lección; no debe ser {@code null}
     */
    public void establecerModulo(Modulo modulo)          { this.modulo = modulo; }

    /**
     * Establece el título de la lección.
     *
     * @param titulo nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void establecerTitulo(String titulo)          { this.titulo = titulo; }

    /**
     * Establece el contenido textual de la lección.
     *
     * @param contenido texto en formato libre; puede ser {@code null}
     */
    public void establecerContenido(String contenido)    { this.contenido = contenido; }

    /**
     * Establece la posición de la lección dentro del módulo.
     *
     * @param ordenIndex índice de orden; valores más bajos se muestran primero
     */
    public void establecerOrdenIndex(int ordenIndex)     { this.ordenIndex = ordenIndex; }

    /**
     * Establece la duración estimada de la lección.
     *
     * @param d duración en minutos; {@code null} indica sin duración definida
     */
    public void establecerDuracionMinutos(Integer d)     { this.duracionMinutos = d; }

    /**
     * Establece el tipo de contenido principal de la lección.
     *
     * @param t tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT)
     */
    public void establecerTipoContenido(String t)        { this.tipoContenido = t; }

    /**
     * Establece la URL del recurso externo o S3.
     *
     * @param u URL del recurso; puede ser {@code null}
     */
    public void establecerUrlContenido(String u)         { this.urlContenido = u; }

    /**
     * Establece si la lección es material suplementario.
     *
     * @param s {@code true} para que la lección no cuente en el progreso del curso
     */
    public void establecerEsSuplementaria(boolean s)     { this.esSuplementaria = s; }
}
