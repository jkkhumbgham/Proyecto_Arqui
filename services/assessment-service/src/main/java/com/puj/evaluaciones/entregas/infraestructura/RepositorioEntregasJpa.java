package com.puj.evaluaciones.entregas.infraestructura;

import com.puj.evaluaciones.entregas.dominio.Entrega;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de entregas.
 *
 * <p>Los métodos de consulta filtran por estado cuando corresponde, excluyendo
 * entregas en estado {@code IN_PROGRESS} de los listados de completados.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioEntregasJpa implements RepositorioEntregas {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    @Override
    public Entrega guardar(Entrega entrega) {
        em.persist(entrega);
        return entrega;
    }

    @Override
    public Entrega actualizar(Entrega entrega) {
        return em.merge(entrega);
    }

    @Override
    public Optional<Entrega> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(Entrega.class, id));
    }

    @Override
    public List<Entrega> buscarPorUsuarioYEvaluacion(UUID idUsuario, UUID idEvaluacion) {
        return em.createQuery(
                "SELECT e FROM Entrega e "
                + "WHERE e.idUsuario = :idUsuario "
                + "AND e.evaluacion.id = :idEvaluacion "
                + "ORDER BY e.iniciadaEn DESC",
                Entrega.class)
                .setParameter("idUsuario", idUsuario)
                .setParameter("idEvaluacion", idEvaluacion)
                .getResultList();
    }

    @Override
    public List<Entrega> buscarPorUsuario(UUID idUsuario, int pagina, int tamano) {
        return em.createQuery(
                "SELECT e FROM Entrega e "
                + "WHERE e.idUsuario = :idUsuario "
                + "ORDER BY e.iniciadaEn DESC",
                Entrega.class)
                .setParameter("idUsuario", idUsuario)
                .setFirstResult(pagina * tamano)
                .setMaxResults(tamano)
                .getResultList();
    }

    @Override
    public List<Entrega> buscarPorEvaluacion(UUID idEvaluacion) {
        return em.createQuery(
                "SELECT e FROM Entrega e "
                + "WHERE e.evaluacion.id = :idEvaluacion "
                + "AND e.estado IN ("
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.SUBMITTED, "
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.GRADED"
                + ") ORDER BY e.entregadaEn DESC",
                Entrega.class)
                .setParameter("idEvaluacion", idEvaluacion)
                .getResultList();
    }

    @Override
    public List<Entrega> buscarCompletadasPorUsuario(UUID idUsuario, int pagina, int tamano) {
        return em.createQuery(
                "SELECT e FROM Entrega e "
                + "WHERE e.idUsuario = :idUsuario "
                + "AND e.estado IN ("
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.SUBMITTED, "
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.GRADED"
                + ") ORDER BY e.entregadaEn DESC",
                Entrega.class)
                .setParameter("idUsuario", idUsuario)
                .setFirstResult(pagina * tamano)
                .setMaxResults(tamano)
                .getResultList();
    }

    @Override
    public List<Entrega> buscarPorUsuarioYEvaluaciones(UUID idUsuario, List<UUID> idEvaluaciones) {
        if (idEvaluaciones == null || idEvaluaciones.isEmpty()) return List.of();
        return em.createQuery(
                "SELECT e FROM Entrega e "
                + "WHERE e.idUsuario = :idUsuario "
                + "AND e.evaluacion.id IN :ids "
                + "AND e.estado IN ("
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.SUBMITTED, "
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.GRADED"
                + ")",
                Entrega.class)
                .setParameter("idUsuario", idUsuario)
                .setParameter("ids", idEvaluaciones)
                .getResultList();
    }
}
