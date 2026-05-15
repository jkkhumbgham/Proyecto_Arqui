package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "study_groups", schema = "collaboration")
public class StudyGroup {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 10;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<GroupMember> members = new ArrayList<>();

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

    public UUID             getId()         { return id; }
    public String           getName()       { return name; }
    public UUID             getCourseId()   { return courseId; }
    public UUID             getOwnerId()    { return ownerId; }
    public int              getMaxMembers() { return maxMembers; }
    public List<GroupMember>getMembers()    { return members; }
    public Instant          getCreatedAt()  { return createdAt; }
    public Instant          getDeletedAt()  { return deletedAt; }

    public void setName(String name)           { this.name = name; }
    public void setCourseId(UUID courseId)     { this.courseId = courseId; }
    public void setOwnerId(UUID ownerId)       { this.ownerId = ownerId; }
    public void setMaxMembers(int maxMembers)  { this.maxMembers = maxMembers; }
}
