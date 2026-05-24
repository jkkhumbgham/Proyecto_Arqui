package com.puj.collaboration.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members", schema = "collaboration",
        uniqueConstraints = @UniqueConstraint(name = "uq_group_member",
                columnNames = {"group_id", "user_id"}))
public class GroupMember {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_group"))
    private StudyGroup group;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "is_tutor", nullable = false)
    private boolean isTutor = false;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        joinedAt = Instant.now();
    }

    public boolean isDeleted()  { return deletedAt != null; }
    public void softDelete()    { this.deletedAt = Instant.now(); }
    public void softRestore()   { this.deletedAt = null; }

    public UUID       getId()       { return id; }
    public StudyGroup getGroup()    { return group; }
    public UUID       getUserId()   { return userId; }
    public boolean    isTutor()     { return isTutor; }
    public Instant    getJoinedAt() { return joinedAt; }
    public Instant    getDeletedAt(){ return deletedAt; }

    public void setGroup(StudyGroup group) { this.group = group; }
    public void setUserId(UUID userId)     { this.userId = userId; }
    public void setTutor(boolean tutor)    { this.isTutor = tutor; }
}
