package com.puj.usuarios.autenticacion.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa una entrada en el registro de auditoría.
 *
 * <p>Cada entrada captura quién realizó una acción, sobre qué recurso, desde
 * qué dirección IP y en qué instante. Los registros son inmutables una vez
 * persistidos; solo el campo {@code detalles} admite actualización posterior.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Entity
@Table(name = "audit_logs", schema = "users")
public class RegistroAuditoria {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID idUsuario;

    @Column(name = "action", nullable = false, length = 100)
    private String accion;

    @Column(name = "resource", length = 200)
    private String recurso;

    @Column(name = "ip_address", length = 50)
    private String direccionIp;

    @Column(name = "details", columnDefinition = "TEXT")
    private String detalles;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant ocurridoEn;

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Asigna identificador y marca de tiempo antes de la primera persistencia.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        ocurridoEn = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Método factory
    // -------------------------------------------------------------------------

    /**
     * Crea una instancia de {@code RegistroAuditoria} con los campos básicos.
     *
     * @param idUsuario   identificador del usuario que realiza la acción;
     *                    puede ser {@code null} para acciones anónimas.
     * @param accion      código de acción auditada (ej. {@code "LOGIN_SUCCESS"}).
     * @param recurso     ruta o descripción del recurso afectado.
     * @param direccionIp dirección IP del cliente originante.
     * @return nueva instancia de {@code RegistroAuditoria} lista para persistir.
     */
    public static RegistroAuditoria crear(UUID idUsuario, String accion,
                                          String recurso, String direccionIp) {
        RegistroAuditoria registro = new RegistroAuditoria();
        registro.idUsuario   = idUsuario;
        registro.accion      = accion;
        registro.recurso     = recurso;
        registro.direccionIp = direccionIp;
        return registro;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return UUID de la entrada. */
    public UUID getId() { return id; }

    /** @return UUID del usuario o {@code null} si la acción fue anónima. */
    public UUID obtenerIdUsuario() { return idUsuario; }

    /** @return código de acción (ej. {@code "ROLE_CHANGE"}). */
    public String obtenerAccion() { return accion; }

    /** @return ruta o descriptor del recurso. */
    public String obtenerRecurso() { return recurso; }

    /** @return dirección IP del cliente. */
    public String obtenerDireccionIp() { return direccionIp; }

    /** @return detalles en texto libre o {@code null} si no se registraron. */
    public String obtenerDetalles() { return detalles; }

    /** @return marca de tiempo de ocurrencia. */
    public Instant obtenerOcurridoEn() { return ocurridoEn; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /** @param detalles texto libre con contexto extra del evento. */
    public void establecerDetalles(String detalles) { this.detalles = detalles; }
}
