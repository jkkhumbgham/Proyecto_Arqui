package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses", schema = "courses")
public class Course {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "max_students")
    private Integer maxStudents;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Module> modules = new ArrayList<>();

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

    public UUID         getId()           { return id; }
    public String       getTitle()        { return title; }
    public String       getDescription()  { return description; }
    public UUID         getInstructorId() { return instructorId; }
    public CourseStatus getStatus()       { return status; }
    public String       getCoverImageUrl(){ return coverImageUrl; }
    public Integer      getMaxStudents()  { return maxStudents; }
    public List<Module> getModules()      { return modules; }
    public Instant      getCreatedAt()    { return createdAt; }
    public Instant      getUpdatedAt()    { return updatedAt; }
    public Instant      getDeletedAt()    { return deletedAt; }

    public void setTitle(String title)               { this.title = title; }
    public void setDescription(String description)   { this.description = description; }
    public void setInstructorId(UUID instructorId)   { this.instructorId = instructorId; }
    public void setStatus(CourseStatus status)        { this.status = status; }
    public void setCoverImageUrl(String url)         { this.coverImageUrl = url; }
    public void setMaxStudents(Integer maxStudents)  { this.maxStudents = maxStudents; }
}
