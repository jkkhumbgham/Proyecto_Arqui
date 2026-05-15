package com.puj.users.repository;

import com.puj.users.entity.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AuditLogRepository {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    public void save(AuditLog log) {
        em.persist(log);
    }

    public List<AuditLog> findByUserId(UUID userId, int page, int size) {
        return em.createQuery(
                        "SELECT a FROM AuditLog a WHERE a.userId = :userId ORDER BY a.occurredAt DESC",
                        AuditLog.class)
                .setParameter("userId", userId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
