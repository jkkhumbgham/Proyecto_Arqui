package com.puj.users.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", schema = "users")
public class AuditLog {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource", length = 200)
    private String resource;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        occurredAt = Instant.now();
    }

    public static AuditLog of(UUID userId, String action, String resource, String ipAddress) {
        AuditLog log = new AuditLog();
        log.userId     = userId;
        log.action     = action;
        log.resource   = resource;
        log.ipAddress  = ipAddress;
        return log;
    }

    public UUID    getId()         { return id; }
    public UUID    getUserId()     { return userId; }
    public String  getAction()     { return action; }
    public String  getResource()   { return resource; }
    public String  getIpAddress()  { return ipAddress; }
    public String  getDetails()    { return details; }
    public Instant getOccurredAt() { return occurredAt; }

    public void setDetails(String details) { this.details = details; }
}
