package com.puj.users.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", schema = "users")
public class RefreshToken {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_user"))
    private User user;

    @Column(name = "token_hash", nullable = false, length = 60)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public boolean isExpired()  { return expiresAt.isBefore(Instant.now()); }
    public boolean isRevoked()  { return revokedAt != null; }
    public boolean isValid()    { return !isExpired() && !isRevoked(); }
    public void    revoke()     { this.revokedAt = Instant.now(); }

    public UUID    getId()        { return id; }
    public User    getUser()      { return user; }
    public String  getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUser(User user)             { this.user = user; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public void setExpiresAt(Instant expiresAt){ this.expiresAt = expiresAt; }
}
