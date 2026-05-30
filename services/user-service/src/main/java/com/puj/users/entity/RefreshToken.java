package com.puj.users.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa un refresh token emitido a un usuario autenticado.
 *
 * <p>El token real nunca se almacena en texto plano; sólo se guarda su hash
 * BCrypt. El estado de validez se determina combinando la fecha de expiración y
 * la fecha de revocación: un token es válido únicamente si no ha expirado y no
 * ha sido revocado explícitamente.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
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

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Asigna identificador y marca de tiempo de creación antes de persistir.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /**
     * Indica si el token ha superado su fecha de expiración.
     *
     * @return {@code true} si el instante actual es posterior a {@code expiresAt}.
     */
    public boolean isExpired() { return expiresAt.isBefore(Instant.now()); }

    /**
     * Indica si el token ha sido revocado explícitamente.
     *
     * @return {@code true} cuando {@code revokedAt} tiene un valor.
     */
    public boolean isRevoked() { return revokedAt != null; }

    /**
     * Indica si el token puede utilizarse para emitir un nuevo access token.
     *
     * @return {@code true} si el token no ha expirado y no ha sido revocado.
     */
    public boolean isValid() { return !isExpired() && !isRevoked(); }

    /**
     * Marca el token como revocado en el instante actual.
     */
    public void revoke() { this.revokedAt = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único del refresh token.
     *
     * @return UUID del token.
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el usuario propietario del token.
     *
     * @return entidad {@link User} asociada.
     */
    public User getUser() { return user; }

    /**
     * Devuelve el hash BCrypt del valor original del token.
     *
     * @return hash de verificación del token.
     */
    public String getTokenHash() { return tokenHash; }

    /**
     * Devuelve el instante en que el token deja de ser válido.
     *
     * @return fecha de expiración.
     */
    public Instant getExpiresAt() { return expiresAt; }

    /**
     * Devuelve el instante en que el token fue revocado.
     *
     * @return fecha de revocación o {@code null} si no ha sido revocado.
     */
    public Instant getRevokedAt() { return revokedAt; }

    /**
     * Devuelve el instante de creación del token.
     *
     * @return fecha de creación.
     */
    public Instant getCreatedAt() { return createdAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Asocia el token con el usuario propietario.
     *
     * @param user entidad {@link User} a la que pertenece el token.
     */
    public void setUser(User user) { this.user = user; }

    /**
     * Establece el hash BCrypt del valor del token.
     *
     * @param tokenHash resultado de {@code BCrypt.hashpw} sobre el token en claro.
     */
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    /**
     * Establece la fecha de expiración del token.
     *
     * @param expiresAt instante a partir del cual el token no será válido.
     */
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
