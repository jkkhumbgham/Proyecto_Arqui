package com.puj.collaboration.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "collaboration")
public class Notification {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID    getId()          { return id; }
    public UUID    getUserId()      { return userId; }
    public String  getType()        { return type; }
    public String  getTitle()       { return title; }
    public String  getBody()        { return body; }
    public String  getReferenceId() { return referenceId; }
    public boolean isRead()         { return read; }
    public Instant getCreatedAt()   { return createdAt; }

    public void setUserId(UUID userId)           { this.userId = userId; }
    public void setType(String type)             { this.type = type; }
    public void setTitle(String title)           { this.title = title; }
    public void setBody(String body)             { this.body = body; }
    public void setReferenceId(String ref)       { this.referenceId = ref; }
    public void markRead()                       { this.read = true; }
}
