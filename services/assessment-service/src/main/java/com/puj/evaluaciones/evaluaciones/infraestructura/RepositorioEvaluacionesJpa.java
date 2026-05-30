package com.puj.evaluaciones.evaluaciones.infraestructura;

import com.puj.evaluaciones.entregas.dominio.EstadoEntrega;
import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;
import com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de evaluaciones.
 *
 * <p>Todas las consultas de listado excluyen evaluaciones eliminadas lógicamente
 * ({@code deleted_at IS NULL}) y ordenan por {@code created_at} ascendente.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioEvaluacionesJpa implements RepositorioEvaluaciones {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    @Override
    public Evaluacion guardar(Evaluacion evaluacion) {
        if (evaluacion.getId() == null) {
            em.persist(evaluacion);
            return evaluacion;
        }
        return em.merge(evaluacion);
    }

    @Override
    public Optional<Evaluacion> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(Evaluacion.class, id))
                .filter(e -> !e.isEliminada());
    }

    @Override
    public List<Evaluacion> buscarTodas() {
        return em.createQuery(
                "SELECT e FROM Evaluacion e "
                + "WHERE e.eliminadoEn IS NULL "
                + "ORDER BY e.creadoEn",
                Evaluacion.class)
                .getResultList();
    }

    @Override
    public List<Evaluacion> buscarPorCurso(UUID idCurso) {
        return em.createQuery(
                "SELECT e FROM Evaluacion e "
                + "WHERE e.idCurso = :idCurso "
                + "AND e.eliminadoEn IS NULL "
                + "ORDER BY e.creadoEn",
                Evaluacion.class)
                .setParameter("idCurso", idCurso)
                .getResultList();
    }

    @Override
    public List<Evaluacion> buscarPorInstructor(UUID idInstructor) {
        return em.createQuery(
                "SELECT e FROM Evaluacion e "
                + "WHERE e.idInstructor = :idInstructor "
                + "AND e.eliminadoEn IS NULL "
                + "ORDER BY e.creadoEn",
                Evaluacion.class)
                .setParameter("idInstructor", idInstructor)
                .getResultList();
    }

    @Override
    public long contarIntentos(UUID idUsuario, UUID idEvaluacion) {
        return em.createQuery(
                "SELECT COUNT(s) FROM Entrega s "
                + "WHERE s.idUsuario = :idUsuario "
                + "AND s.evaluacion.id = :idEvaluacion "
                + "AND s.estado IN ("
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.SUBMITTED, "
                + "  com.puj.evaluaciones.entregas.dominio.EstadoEntrega.GRADED"
                + ")",
                Long.class)
                .setParameter("idUsuario", idUsuario)
                .setParameter("idEvaluacion", idEvaluacion)
                .getSingleResult();
    }
}
