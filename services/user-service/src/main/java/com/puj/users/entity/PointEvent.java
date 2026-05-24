package com.puj.users.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_events", schema = "users")
public class PointEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID    getId()          { return id; }
    public UUID    getUserId()      { return userId; }
    public String  getActionType()  { return actionType; }
    public int     getPoints()      { return points; }
    public String  getReferenceId() { return referenceId; }
    public Instant getCreatedAt()   { return createdAt; }

    public void setUserId(UUID userId)           { this.userId = userId; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setPoints(int points)            { this.points = points; }
    public void setReferenceId(String ref)       { this.referenceId = ref; }
}
