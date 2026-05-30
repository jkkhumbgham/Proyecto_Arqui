package com.puj.users.entity;

import com.puj.security.rbac.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa un usuario registrado en la plataforma.
 *
 * <p>Incluye soporte para bloqueo temporal por intentos fallidos de inicio de
 * sesión (lockout), borrado lógico (soft-delete) y registro del consentimiento
 * de tratamiento de datos personales conforme a la Ley 1581/2012.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Entity
@Table(name = "users", schema = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class User {

    /** Número máximo de intentos fallidos antes de bloquear la cuenta. */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** Duración del bloqueo temporal en segundos (15 minutos). */
    private static final long LOCKOUT_SECONDS = 900L;

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

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Asigna identificador y marcas de tiempo antes de la primera persistencia.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Actualiza la marca de tiempo de modificación antes de cada actualización.
     */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /**
     * Indica si el usuario ha sido eliminado lógicamente.
     *
     * @return {@code true} cuando {@code deletedAt} tiene un valor distinto de null.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Indica si la cuenta se encuentra bloqueada por intentos fallidos de
     * autenticación.
     *
     * @return {@code true} si el instante actual es anterior a {@code lockedUntil}.
     */
    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Realiza el borrado lógico del usuario marcando la fecha de eliminación y
     * desactivando la cuenta.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active    = false;
    }

    /**
     * Registra un intento de autenticación fallido e impone bloqueo temporal
     * cuando se alcanza el límite de intentos permitidos.
     */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCKOUT_SECONDS);
        }
    }

    /**
     * Restablece los contadores de seguridad y actualiza la marca de último
     * inicio de sesión exitoso.
     */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil  = null;
        this.lastLoginAt  = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único del usuario.
     *
     * @return UUID del usuario.
     */
    public UUID getId() { return id; }

    /**
     * Devuelve la dirección de correo electrónico del usuario.
     *
     * @return correo electrónico en minúsculas.
     */
    public String getEmail() { return email; }

    /**
     * Devuelve el hash BCrypt de la contraseña almacenada.
     *
     * @return hash de la contraseña.
     */
    public String getPasswordHash() { return passwordHash; }

    /**
     * Devuelve el nombre de pila del usuario.
     *
     * @return nombre de pila.
     */
    public String getFirstName() { return firstName; }

    /**
     * Devuelve el apellido del usuario.
     *
     * @return apellido.
     */
    public String getLastName() { return lastName; }

    /**
     * Devuelve el rol de seguridad asignado al usuario.
     *
     * @return rol RBAC.
     */
    public Role getRole() { return role; }

    /**
     * Indica si la cuenta está activa.
     *
     * @return {@code true} si la cuenta está activa.
     */
    public boolean isActive() { return active; }

    /**
     * Indica si el usuario otorgó consentimiento de tratamiento de datos.
     *
     * @return {@code true} si el consentimiento fue otorgado.
     */
    public boolean isConsentGiven() { return consentGiven; }

    /**
     * Devuelve el instante en que se registró el consentimiento.
     *
     * @return fecha de consentimiento o {@code null} si no aplica.
     */
    public Instant getConsentDate() { return consentDate; }

    /**
     * Devuelve el instante del último inicio de sesión exitoso.
     *
     * @return última fecha de login o {@code null} si nunca ha iniciado sesión.
     */
    public Instant getLastLoginAt() { return lastLoginAt; }

    /**
     * Devuelve el número acumulado de intentos fallidos de autenticación.
     *
     * @return contador de intentos fallidos.
     */
    public int getFailedLoginAttempts() { return failedLoginAttempts; }

    /**
     * Devuelve el instante hasta el cual la cuenta está bloqueada.
     *
     * @return instante de desbloqueo o {@code null} si no está bloqueada.
     */
    public Instant getLockedUntil() { return lockedUntil; }

    /**
     * Devuelve el instante de creación del registro.
     *
     * @return fecha de creación.
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Devuelve el instante de la última modificación del registro.
     *
     * @return fecha de última actualización.
     */
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Devuelve el instante de eliminación lógica del registro.
     *
     * @return fecha de borrado o {@code null} si el usuario no ha sido eliminado.
     */
    public Instant getDeletedAt() { return deletedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece la dirección de correo electrónico del usuario.
     *
     * @param email correo electrónico normalizado (minúsculas, sin espacios).
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * Establece el hash BCrypt de la contraseña.
     *
     * @param passwordHash hash generado con {@code BCrypt.hashpw}.
     */
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    /**
     * Establece el nombre de pila del usuario.
     *
     * @param firstName nombre de pila sin espacios sobrantes.
     */
    public void setFirstName(String firstName) { this.firstName = firstName; }

    /**
     * Establece el apellido del usuario.
     *
     * @param lastName apellido sin espacios sobrantes.
     */
    public void setLastName(String lastName) { this.lastName = lastName; }

    /**
     * Establece el rol RBAC del usuario.
     *
     * @param role rol de seguridad a asignar.
     */
    public void setRole(Role role) { this.role = role; }

    /**
     * Activa o desactiva la cuenta del usuario.
     *
     * @param active {@code true} para activar, {@code false} para desactivar.
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Registra si el usuario otorgó consentimiento de tratamiento de datos.
     *
     * @param consent {@code true} si el consentimiento fue otorgado.
     */
    public void setConsentGiven(boolean consent) { this.consentGiven = consent; }

    /**
     * Establece el instante en que se registró el consentimiento.
     *
     * @param consentDate fecha y hora del consentimiento.
     */
    public void setConsentDate(Instant consentDate) { this.consentDate = consentDate; }

    /**
     * Establece la fecha del último inicio de sesión.
     *
     * @param lastLogin instante del último login exitoso.
     */
    public void setLastLoginAt(Instant lastLogin) { this.lastLoginAt = lastLogin; }
}
