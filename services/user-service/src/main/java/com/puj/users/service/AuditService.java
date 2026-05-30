package com.puj.users.service;

import com.puj.users.entity.AuditLog;
import com.puj.users.repository.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de auditoría para el registro de eventos de seguridad y operaciones
 * sensibles sobre usuarios.
 *
 * <p>Cada llamada a {@link #log} se ejecuta en una transacción independiente
 * ({@code REQUIRES_NEW}) para garantizar que el registro quede grabado incluso
 * cuando la transacción principal falle (ej. credenciales incorrectas, cuenta
 * bloqueada). Los fallos al persistir la entrada se capturan y registran como
 * advertencias en el log del sistema sin propagar la excepción.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    @Inject
    private AuditLogRepository auditRepo;

    /**
     * Persiste una entrada de auditoría en una transacción independiente.
     *
     * <p>Si la persistencia falla, la excepción se captura internamente y se
     * emite una advertencia en el log del sistema, evitando que un fallo de
     * auditoría interrumpa el flujo principal de la aplicación.
     *
     * @param userId   UUID del usuario que realiza la acción; puede ser
     *                 {@code null} para eventos anónimos (ej. intento de login
     *                 con correo inexistente).
     * @param action   código descriptivo de la acción auditada
     *                 (ej. {@code "LOGIN_SUCCESS"}, {@code "ROLE_CHANGE"}).
     * @param resource ruta o identificador del recurso afectado.
     * @param ip       dirección IP del cliente que origina la acción.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(UUID userId, String action, String resource, String ip) {
        try {
            auditRepo.save(AuditLog.of(userId, action, resource, ip));
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to persist audit log — action=" + action, e);
        }
    }
}
