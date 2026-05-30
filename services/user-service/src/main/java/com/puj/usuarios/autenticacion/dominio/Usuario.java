package com.puj.usuarios.autenticacion.dominio;

import com.puj.seguridad.rbac.Rol;
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
        indexes = { @Index(name = "ix_users_email", columnList = "email") },
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class Usuario {

    /** Número máximo de intentos fallidos antes de bloquear la cuenta. */
    private static final int MAX_INTENTOS_FALLIDOS = 5;

    /** Duración del bloqueo temporal en segundos (15 minutos). */
    private static final long SEGUNDOS_BLOQUEO = 900L;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, length = 255)
    private String correo;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String hashContrasena;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String apellido;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "active", nullable = false)
    private boolean activo = true;

    @Column(name = "consent_given", nullable = false)
    private boolean consentimientoDado = false;

    @Column(name = "consent_date")
    private Instant fechaConsentimiento;

    @Column(name = "last_login_at")
    private Instant ultimoAcceso;

    @Column(name = "failed_login_attempts", nullable = false)
    private int intentosFallidos = 0;

    @Column(name = "locked_until")
    private Instant bloqueadoHasta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Asigna identificador y marcas de tiempo antes de la primera persistencia.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        Instant ahora = Instant.now();
        creadoEn     = ahora;
        actualizadoEn = ahora;
    }

    /**
     * Actualiza la marca de tiempo de modificación antes de cada actualización.
     */
    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Métodos de dominio
    // -------------------------------------------------------------------------

    /**
     * Indica si el usuario ha sido eliminado lógicamente.
     *
     * @return {@code true} cuando {@code eliminadoEn} tiene un valor distinto de null.
     */
    public boolean estaEliminado() {
        return eliminadoEn != null;
    }

    /**
     * Indica si la cuenta se encuentra bloqueada por intentos fallidos de
     * autenticación.
     *
     * @return {@code true} si el instante actual es anterior a {@code bloqueadoHasta}.
     */
    public boolean estaBloqueado() {
        return bloqueadoHasta != null && Instant.now().isBefore(bloqueadoHasta);
    }

    /**
     * Realiza el borrado lógico del usuario marcando la fecha de eliminación y
     * desactivando la cuenta.
     */
    public void eliminarLogicamente() {
        this.eliminadoEn = Instant.now();
        this.activo      = false;
    }

    /**
     * Registra un intento de autenticación fallido e impone bloqueo temporal
     * cuando se alcanza el límite de intentos permitidos.
     */
    public void registrarIntentoFallido() {
        this.intentosFallidos++;
        if (this.intentosFallidos >= MAX_INTENTOS_FALLIDOS) {
            this.bloqueadoHasta = Instant.now().plusSeconds(SEGUNDOS_BLOQUEO);
        }
    }

    /**
     * Restablece los contadores de seguridad y actualiza la marca de último
     * inicio de sesión exitoso.
     */
    public void registrarAccesoExitoso() {
        this.intentosFallidos = 0;
        this.bloqueadoHasta   = null;
        this.ultimoAcceso     = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return UUID del usuario. */
    public UUID getId() { return id; }

    /** @return correo electrónico en minúsculas. */
    public String obtenerCorreo() { return correo; }

    /** @return hash de la contraseña. */
    public String obtenerHashContrasena() { return hashContrasena; }

    /** @return nombre de pila. */
    public String obtenerNombre() { return nombre; }

    /** @return apellido. */
    public String obtenerApellido() { return apellido; }

    /** @return rol RBAC. */
    public Rol obtenerRol() { return rol; }

    /** @return {@code true} si la cuenta está activa. */
    public boolean esActivo() { return activo; }

    /** @return {@code true} si el consentimiento fue otorgado. */
    public boolean esConsentimientoDado() { return consentimientoDado; }

    /** @return fecha de consentimiento o {@code null} si no aplica. */
    public Instant obtenerFechaConsentimiento() { return fechaConsentimiento; }

    /** @return última fecha de acceso o {@code null} si nunca ha iniciado sesión. */
    public Instant obtenerUltimoAcceso() { return ultimoAcceso; }

    /** @return contador de intentos fallidos. */
    public int obtenerIntentosFallidos() { return intentosFallidos; }

    /** @return instante de desbloqueo o {@code null} si no está bloqueada. */
    public Instant obtenerBloqueadoHasta() { return bloqueadoHasta; }

    /** @return fecha de creación. */
    public Instant obtenerCreadoEn() { return creadoEn; }

    /** @return fecha de última actualización. */
    public Instant obtenerActualizadoEn() { return actualizadoEn; }

    /** @return fecha de borrado o {@code null} si no ha sido eliminado. */
    public Instant obtenerEliminadoEn() { return eliminadoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /** @param correo correo electrónico normalizado (minúsculas, sin espacios). */
    public void establecerCorreo(String correo) { this.correo = correo; }

    /** @param hashContrasena hash generado con {@code BCrypt.hashpw}. */
    public void establecerHashContrasena(String hashContrasena) { this.hashContrasena = hashContrasena; }

    /** @param nombre nombre de pila sin espacios sobrantes. */
    public void establecerNombre(String nombre) { this.nombre = nombre; }

    /** @param apellido apellido sin espacios sobrantes. */
    public void establecerApellido(String apellido) { this.apellido = apellido; }

    /** @param rol rol de seguridad a asignar. */
    public void establecerRol(Rol rol) { this.rol = rol; }

    /** @param activo {@code true} para activar, {@code false} para desactivar. */
    public void establecerActivo(boolean activo) { this.activo = activo; }

    /** @param consentimientoDado {@code true} si el consentimiento fue otorgado. */
    public void establecerConsentimientoDado(boolean consentimientoDado) { this.consentimientoDado = consentimientoDado; }

    /** @param fechaConsentimiento fecha y hora del consentimiento. */
    public void establecerFechaConsentimiento(Instant fechaConsentimiento) { this.fechaConsentimiento = fechaConsentimiento; }

    /** @param ultimoAcceso instante del último acceso exitoso. */
    public void establecerUltimoAcceso(Instant ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
}
