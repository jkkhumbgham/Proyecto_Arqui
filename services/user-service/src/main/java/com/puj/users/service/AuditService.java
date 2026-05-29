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
 * Persiste entradas en el log de auditoría usando una transacción independiente
 * (REQUIRES_NEW) para garantizar que el registro se grabe incluso cuando la
 * transacción principal haya fallado (ej. login incorrecto, cuenta bloqueada).
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    @Inject
    private AuditLogRepository auditRepo;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(UUID userId, String action, String resource, String ip) {
        try {
            auditRepo.save(AuditLog.of(userId, action, resource, ip));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to persist audit log — action=" + action, e);
        }
    }
}
