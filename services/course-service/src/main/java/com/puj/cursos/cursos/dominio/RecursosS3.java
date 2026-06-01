package com.puj.cursos.cursos.dominio;

import com.puj.cursos.lecciones.dominio.Leccion;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa un archivo almacenado en S3 adjunto a una {@link Leccion lección}.
 *
 * <p>Cada recurso tiene una clave S3 ({@code claveS3}) que permite generar URLs pre-firmadas
 * de descarga. El borrado lógico se implementa mediante {@link #eliminarLogicamente()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "s3_resources", schema = "courses")
public class RecursosS3 {

    /** Identificador único del recurso S3, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Lección a la que está adjunto este recurso. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name       = "lesson_id",
            nullable   = false,
            foreignKey = @ForeignKey(name = "fk_resource_lesson"))
    private Leccion leccion;

    /** Clave S3 del objeto (ruta completa dentro del bucket). */
    @Column(name = "s3_key", nullable = false, length = 500)
    private String claveS3;

    /** Nombre original del archivo subido por el usuario. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String nombreArchivo;

    /** Tipo MIME del archivo (ej. {@code application/pdf}); puede ser {@code null}. */
    @Column(name = "content_type", length = 100)
    private String tipoContenido;

    /** Tamaño del archivo en bytes; puede ser {@code null} si no se reportó. */
    @Column(name = "file_size_bytes")
    private Long tamanoBytes;

    /** Marca temporal del momento en que se subió el archivo; inmutable. */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant subidoEn;

    /** Marca temporal de borrado lógico; {@code null} si el recurso está activo. */
    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code subidoEn} antes de la primera persistencia.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        subidoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si el recurso ha sido borrado lógicamente.
     *
     * @return {@code true} si {@code eliminadoEn} tiene un valor distinto de {@code null}
     */
    public boolean estaEliminado() { return eliminadoEn != null; }

    /**
     * Realiza el borrado lógico del recurso asignando la marca temporal actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del recurso S3 */
    public UUID    getId()              { return id; }

    /** @return lección a la que está adjunto el recurso */
    public Leccion obtenerLeccion()     { return leccion; }

    /** @return clave S3 del objeto dentro del bucket */
    public String  obtenerClaveS3()     { return claveS3; }

    /** @return nombre original del archivo */
    public String  obtenerNombreArchivo() { return nombreArchivo; }

    /** @return tipo MIME del archivo, puede ser {@code null} */
    public String  obtenerTipoContenido() { return tipoContenido; }

    /** @return tamaño del archivo en bytes, puede ser {@code null} */
    public Long    obtenerTamanoBytes() { return tamanoBytes; }

    /** @return marca temporal de carga del archivo */
    public Instant obtenerSubidoEn()    { return subidoEn; }

    /** @return marca temporal de borrado lógico, {@code null} si activo */
    public Instant obtenerEliminadoEn() { return eliminadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna la lección a la que pertenece este recurso.
     *
     * @param leccion lección padre; no debe ser {@code null}
     */
    public void establecerLeccion(Leccion leccion)           { this.leccion = leccion; }

    /**
     * Establece la clave S3 del recurso.
     *
     * @param claveS3 ruta completa dentro del bucket S3; no debe ser {@code null}
     */
    public void establecerClaveS3(String claveS3)            { this.claveS3 = claveS3; }

    /**
     * Establece el nombre original del archivo.
     *
     * @param nombreArchivo nombre del archivo tal como lo subió el usuario
     */
    public void establecerNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    /**
     * Establece el tipo MIME del archivo.
     *
     * @param tipoContenido tipo MIME (ej. {@code application/pdf}); puede ser {@code null}
     */
    public void establecerTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }

    /**
     * Establece el tamaño del archivo en bytes.
     *
     * @param tamano tamaño en bytes; puede ser {@code null} si no se conoce
     */
    public void establecerTamanoBytes(Long tamano)            { this.tamanoBytes = tamano; }
}
