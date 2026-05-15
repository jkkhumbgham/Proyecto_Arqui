package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lessons", schema = "courses")
public class Lesson {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lesson_module"))
    private Module module;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<S3Resource> resources = new ArrayList<>();

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

    public UUID           getId()              { return id; }
    public Module         getModule()          { return module; }
    public String         getTitle()           { return title; }
    public String         getContent()         { return content; }
    public int            getOrderIndex()      { return orderIndex; }
    public Integer        getDurationMinutes() { return durationMinutes; }
    public List<S3Resource> getResources()     { return resources; }
    public Instant        getCreatedAt()       { return createdAt; }
    public Instant        getDeletedAt()       { return deletedAt; }

    public void setModule(Module module)                 { this.module = module; }
    public void setTitle(String title)                   { this.title = title; }
    public void setContent(String content)               { this.content = content; }
    public void setOrderIndex(int orderIndex)            { this.orderIndex = orderIndex; }
    public void setDurationMinutes(Integer d)            { this.durationMinutes = d; }
}
