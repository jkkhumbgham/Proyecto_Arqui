package com.puj.courses.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "s3_resources", schema = "courses")
public class S3Resource {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_resource_lesson"))
    private Lesson lesson;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        uploadedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID    getId()           { return id; }
    public Lesson  getLesson()       { return lesson; }
    public String  getS3Key()        { return s3Key; }
    public String  getFileName()     { return fileName; }
    public String  getContentType()  { return contentType; }
    public Long    getFileSizeBytes(){ return fileSizeBytes; }
    public Instant getUploadedAt()   { return uploadedAt; }
    public Instant getDeletedAt()    { return deletedAt; }

    public void setLesson(Lesson lesson)             { this.lesson = lesson; }
    public void setS3Key(String s3Key)               { this.s3Key = s3Key; }
    public void setFileName(String fileName)         { this.fileName = fileName; }
    public void setContentType(String contentType)   { this.contentType = contentType; }
    public void setFileSizeBytes(Long size)          { this.fileSizeBytes = size; }
}
