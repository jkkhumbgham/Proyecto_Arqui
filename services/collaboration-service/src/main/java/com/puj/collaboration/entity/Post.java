package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID    getId()        { return id; }
    public Thread  getThread()    { return thread; }
    public UUID    getAuthorId()  { return authorId; }
    public String  getContent()   { return content; }
    public boolean isEdited()     { return edited; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setThread(Thread thread)     { this.thread = thread; }
    public void setAuthorId(UUID authorId)   { this.authorId = authorId; }
    public void setContent(String content)   { this.content = content; }
    public void setEdited(boolean edited)    { this.edited = edited; }
}
