package com.puj.usuarios.autenticacion.infraestructura;

import com.puj.usuarios.autenticacion.dominio.RegistroAuditoria;
import com.puj.usuarios.autenticacion.dominio.RepositorioAuditoria;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.UUID;

/**
 * Implementación JPA de {@link RepositorioAuditoria}.
 *
 * <p>Proporciona operaciones de escritura y consulta sobre el registro de
 * auditoría. Los registros de auditoría son inmutables una vez persistidos.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class RepositorioAuditoriaJpa implements RepositorioAuditoria {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    /** {@inheritDoc} */
    @Override
    public void guardar(RegistroAuditoria registro) {
        em.persist(registro);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resultados ordenados por fecha de ocurrencia descendente.
     */
    @Override
    public List<RegistroAuditoria> buscarPorUsuario(UUID idUsuario, int pagina, int cantidad) {
        return em.createQuery(
                        "SELECT a FROM RegistroAuditoria a"
                        + " WHERE a.idUsuario = :idUsuario"
                        + " ORDER BY a.ocurridoEn DESC",
                        RegistroAuditoria.class)
                .setParameter("idUsuario", idUsuario)
                .setFirstResult(pagina * cantidad)
                .setMaxResults(cantidad)
                .getResultList();
    }
}
