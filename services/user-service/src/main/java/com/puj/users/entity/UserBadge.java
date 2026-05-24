package com.puj.users.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_badges", schema = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_code"}))
public class UserBadge {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "badge_code", nullable = false, length = 50)
    private String badgeCode;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (earnedAt == null) earnedAt = Instant.now();
    }

    public UUID    getId()        { return id; }
    public UUID    getUserId()    { return userId; }
    public String  getBadgeCode() { return badgeCode; }
    public Instant getEarnedAt()  { return earnedAt; }

    public void setUserId(UUID userId)         { this.userId = userId; }
    public void setBadgeCode(String badgeCode) { this.badgeCode = badgeCode; }
}
