package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "forums", schema = "collaboration")
public class Forum {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "forum", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<Thread> threads = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID         getId()          { return id; }
    public String       getTitle()       { return title; }
    public UUID         getCourseId()    { return courseId; }
    public UUID         getCreatedBy()   { return createdBy; }
    public String       getDescription() { return description; }
    public List<Thread> getThreads()     { return threads; }
    public Instant      getCreatedAt()   { return createdAt; }
    public Instant      getDeletedAt()   { return deletedAt; }

    public void setTitle(String title)             { this.title = title; }
    public void setCourseId(UUID courseId)         { this.courseId = courseId; }
    public void setCreatedBy(UUID createdBy)       { this.createdBy = createdBy; }
    public void setDescription(String description) { this.description = description; }
}
