package com.puj.users.repository;

import com.puj.users.entity.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link AuditLog}.
 *
 * <p>Proporciona operaciones de escritura y consulta sobre el registro de
 * auditoría. Los registros de auditoría son inmutables una vez persistidos.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class AuditLogRepository {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    /**
     * Persiste una nueva entrada de auditoría en la base de datos.
     *
     * @param log entrada de auditoría a guardar; no debe ser {@code null}.
     */
    public void save(AuditLog log) {
        em.persist(log);
    }

    /**
     * Devuelve una página de entradas de auditoría para un usuario dado,
     * ordenadas por fecha de ocurrencia descendente.
     *
     * @param userId UUID del usuario cuyos eventos se desean consultar.
     * @param page   número de página (basado en cero).
     * @param size   número máximo de resultados por página.
     * @return lista de entradas de auditoría del usuario.
     */
    public List<AuditLog> findByUserId(UUID userId, int page, int size) {
        return em.createQuery(
                        "SELECT a FROM AuditLog a"
                        + " WHERE a.userId = :userId"
                        + " ORDER BY a.occurredAt DESC",
                        AuditLog.class)
                .setParameter("userId", userId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
