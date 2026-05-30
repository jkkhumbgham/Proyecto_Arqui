package com.puj.courses.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa un archivo almacenado en S3 adjunto a una {@link Lesson lección}.
 *
 * <p>Cada recurso tiene una clave S3 ({@code s3Key}) que permite generar URLs pre-firmadas
 * de descarga a través de {@link com.puj.courses.service.S3Service}. El borrado lógico se
 * implementa mediante {@link #softDelete()}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "s3_resources", schema = "courses")
public class S3Resource {

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
    private Lesson lesson;

    /** Clave S3 del objeto (ruta completa dentro del bucket). */
    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    /** Nombre original del archivo subido por el usuario. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Tipo MIME del archivo (ej. {@code application/pdf}); puede ser {@code null}. */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** Tamaño del archivo en bytes; puede ser {@code null} si no se reportó. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Marca temporal del momento en que se subió el archivo; inmutable. */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    /** Marca temporal de borrado lógico; {@code null} si el recurso está activo. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // -------------------------------------------------------------------------
    // Callback JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id} y {@code uploadedAt} antes de la primera persistencia.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        uploadedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si el recurso ha sido borrado lógicamente.
     *
     * @return {@code true} si {@code deletedAt} tiene un valor distinto de {@code null}
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Realiza el borrado lógico del recurso asignando la marca temporal actual.
     */
    public void softDelete() { this.deletedAt = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del recurso S3 */
    public UUID    getId()            { return id; }

    /** @return lección a la que está adjunto el recurso */
    public Lesson  getLesson()        { return lesson; }

    /** @return clave S3 del objeto dentro del bucket */
    public String  getS3Key()         { return s3Key; }

    /** @return nombre original del archivo */
    public String  getFileName()      { return fileName; }

    /** @return tipo MIME del archivo, puede ser {@code null} */
    public String  getContentType()   { return contentType; }

    /** @return tamaño del archivo en bytes, puede ser {@code null} */
    public Long    getFileSizeBytes() { return fileSizeBytes; }

    /** @return marca temporal de carga del archivo */
    public Instant getUploadedAt()    { return uploadedAt; }

    /** @return marca temporal de borrado lógico, {@code null} si activo */
    public Instant getDeletedAt()     { return deletedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asigna la lección a la que pertenece este recurso.
     *
     * @param lesson lección padre; no debe ser {@code null}
     */
    public void setLesson(Lesson lesson)           { this.lesson = lesson; }

    /**
     * Establece la clave S3 del recurso.
     *
     * @param s3Key ruta completa dentro del bucket S3; no debe ser {@code null}
     */
    public void setS3Key(String s3Key)             { this.s3Key = s3Key; }

    /**
     * Establece el nombre original del archivo.
     *
     * @param fileName nombre del archivo tal como lo subió el usuario; no debe ser {@code null}
     */
    public void setFileName(String fileName)       { this.fileName = fileName; }

    /**
     * Establece el tipo MIME del archivo.
     *
     * @param contentType tipo MIME (ej. {@code application/pdf}); puede ser {@code null}
     */
    public void setContentType(String contentType) { this.contentType = contentType; }

    /**
     * Establece el tamaño del archivo en bytes.
     *
     * @param size tamaño en bytes; puede ser {@code null} si no se conoce
     */
    public void setFileSizeBytes(Long size)        { this.fileSizeBytes = size; }
}
