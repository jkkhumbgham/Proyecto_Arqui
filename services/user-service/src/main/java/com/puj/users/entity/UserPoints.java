package com.puj.users.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_points", schema = "users")
public class UserPoints {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    @PrePersist
    void onCreate() { if (id == null) id = UUID.randomUUID(); }

    public UUID getId()          { return id; }
    public UUID getUserId()      { return userId; }
    public int  getTotalPoints() { return totalPoints; }

    public void setUserId(UUID userId)       { this.userId = userId; }
    public void setTotalPoints(int pts)      { this.totalPoints = pts; }
    public void addPoints(int pts)           { this.totalPoints += pts; }
}
