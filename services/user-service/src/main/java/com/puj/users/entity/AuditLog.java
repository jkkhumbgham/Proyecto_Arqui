package com.puj.users.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa una entrada en el registro de auditoría.
 *
 * <p>Cada entrada captura quién realizó una acción, sobre qué recurso, desde
 * qué dirección IP y en qué instante. Los registros son inmutables una vez
 * persistidos; solo el campo {@code details} admite actualización posterior.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
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

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Asigna identificador y marca de tiempo antes de la primera persistencia.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        occurredAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Factory method
    // -------------------------------------------------------------------------

    /**
     * Crea una instancia de {@code AuditLog} con los campos básicos de auditoría.
     *
     * @param userId     identificador del usuario que realiza la acción;
     *                   puede ser {@code null} para acciones anónimas.
     * @param action     código de acción auditada (ej. {@code "LOGIN_SUCCESS"}).
     * @param resource   ruta o descripción del recurso afectado.
     * @param ipAddress  dirección IP del cliente originante.
     * @return nueva instancia de {@code AuditLog} lista para persistir.
     */
    public static AuditLog of(UUID userId, String action,
                               String resource, String ipAddress) {
        AuditLog log = new AuditLog();
        log.userId    = userId;
        log.action    = action;
        log.resource  = resource;
        log.ipAddress = ipAddress;
        return log;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el identificador único de la entrada de auditoría.
     *
     * @return UUID de la entrada.
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el identificador del usuario que originó la acción.
     *
     * @return UUID del usuario o {@code null} si la acción fue anónima.
     */
    public UUID getUserId() { return userId; }

    /**
     * Devuelve el código de la acción auditada.
     *
     * @return código de acción (ej. {@code "ROLE_CHANGE"}).
     */
    public String getAction() { return action; }

    /**
     * Devuelve el identificador del recurso afectado por la acción.
     *
     * @return ruta o descriptor del recurso.
     */
    public String getResource() { return resource; }

    /**
     * Devuelve la dirección IP desde la que se originó la acción.
     *
     * @return dirección IP del cliente.
     */
    public String getIpAddress() { return ipAddress; }

    /**
     * Devuelve información adicional asociada al evento de auditoría.
     *
     * @return detalles en texto libre o {@code null} si no se registraron.
     */
    public String getDetails() { return details; }

    /**
     * Devuelve el instante en que ocurrió la acción auditada.
     *
     * @return marca de tiempo de ocurrencia.
     */
    public Instant getOccurredAt() { return occurredAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece información adicional sobre el evento de auditoría.
     *
     * @param details texto libre con contexto extra del evento.
     */
    public void setDetails(String details) { this.details = details; }
}
