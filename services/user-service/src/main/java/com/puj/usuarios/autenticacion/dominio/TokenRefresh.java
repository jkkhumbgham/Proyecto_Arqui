package com.puj.usuarios.autenticacion.dominio;

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
public class TokenRefresh {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_user"))
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 60)
    private String hashToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiraEn;

    @Column(name = "revoked_at")
    private Instant revocadoEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Asigna identificador y marca de tiempo de creación antes de persistir.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        creadoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Métodos de dominio
    // -------------------------------------------------------------------------

    /**
     * Indica si el token ha superado su fecha de expiración.
     *
     * @return {@code true} si el instante actual es posterior a {@code expiraEn}.
     */
    public boolean estaExpirado() { return expiraEn.isBefore(Instant.now()); }

    /**
     * Indica si el token ha sido revocado explícitamente.
     *
     * @return {@code true} cuando {@code revocadoEn} tiene un valor.
     */
    public boolean estaRevocado() { return revocadoEn != null; }

    /**
     * Indica si el token puede utilizarse para emitir un nuevo access token.
     *
     * @return {@code true} si el token no ha expirado y no ha sido revocado.
     */
    public boolean esValido() { return !estaExpirado() && !estaRevocado(); }

    /**
     * Marca el token como revocado en el instante actual.
     */
    public void revocar() { this.revocadoEn = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return UUID del token. */
    public UUID getId() { return id; }

    /** @return entidad {@link Usuario} asociada. */
    public Usuario obtenerUsuario() { return usuario; }

    /** @return hash de verificación del token. */
    public String obtenerHashToken() { return hashToken; }

    /** @return fecha de expiración. */
    public Instant obtenerExpiraEn() { return expiraEn; }

    /** @return fecha de revocación o {@code null} si no ha sido revocado. */
    public Instant obtenerRevocadoEn() { return revocadoEn; }

    /** @return fecha de creación. */
    public Instant obtenerCreadoEn() { return creadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /** @param usuario entidad {@link Usuario} a la que pertenece el token. */
    public void establecerUsuario(Usuario usuario) { this.usuario = usuario; }

    /** @param hashToken resultado de {@code BCrypt.hashpw} sobre el token en claro. */
    public void establecerHashToken(String hashToken) { this.hashToken = hashToken; }

    /** @param expiraEn instante a partir del cual el token no será válido. */
    public void establecerExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
}
