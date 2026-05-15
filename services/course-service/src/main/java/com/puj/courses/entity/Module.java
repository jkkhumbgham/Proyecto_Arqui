package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "modules", schema = "courses")
public class Module {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_module_course"))
    private Course course;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Lesson> lessons = new ArrayList<>();

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
    public Course       getCourse()      { return course; }
    public String       getTitle()       { return title; }
    public String       getDescription() { return description; }
    public int          getOrderIndex()  { return orderIndex; }
    public List<Lesson> getLessons()     { return lessons; }
    public Instant      getCreatedAt()   { return createdAt; }
    public Instant      getDeletedAt()   { return deletedAt; }

    public void setCourse(Course course)             { this.course = course; }
    public void setTitle(String title)               { this.title = title; }
    public void setDescription(String description)   { this.description = description; }
    public void setOrderIndex(int orderIndex)        { this.orderIndex = orderIndex; }
}
