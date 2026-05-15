package com.puj.users.entity;

import com.puj.security.rbac.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class User {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    @Column(name = "consent_date")
    private Instant consentDate;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

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
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isLocked()  { return lockedUntil != null && Instant.now().isBefore(lockedUntil); }

    public void softDelete() { this.deletedAt = Instant.now(); this.active = false; }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = Instant.now().plusSeconds(900); // 15 min
        }
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public UUID    getId()           { return id; }
    public String  getEmail()        { return email; }
    public String  getPasswordHash() { return passwordHash; }
    public String  getFirstName()    { return firstName; }
    public String  getLastName()     { return lastName; }
    public Role    getRole()         { return role; }
    public boolean isActive()        { return active; }
    public boolean isConsentGiven()  { return consentGiven; }
    public Instant getConsentDate()  { return consentDate; }
    public Instant getLastLoginAt()         { return lastLoginAt; }
    public int     getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil()         { return lockedUntil; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }
    public Instant getDeletedAt()    { return deletedAt; }

    public void setEmail(String email)               { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setFirstName(String firstName)       { this.firstName = firstName; }
    public void setLastName(String lastName)         { this.lastName = lastName; }
    public void setRole(Role role)                   { this.role = role; }
    public void setActive(boolean active)            { this.active = active; }
    public void setConsentGiven(boolean consent)     { this.consentGiven = consent; }
    public void setConsentDate(Instant consentDate)  { this.consentDate = consentDate; }
    public void setLastLoginAt(Instant lastLogin)    { this.lastLoginAt = lastLogin; }
}
