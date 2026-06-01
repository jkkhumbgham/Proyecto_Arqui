package com.puj.evaluaciones.adaptativo.infraestructura;

import com.puj.evaluaciones.adaptativo.dominio.ReglaAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.RepositorioReglasAdaptativas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de reglas adaptativas.
 *
 * <p>Todas las consultas excluyen reglas eliminadas lógicamente
 * ({@code deleted_at IS NULL}) y en las búsquedas activas también
 * se filtra por {@code active = true}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioReglasAdaptativasJpa implements RepositorioReglasAdaptativas {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    @Override
    public ReglaAdaptativa guardar(ReglaAdaptativa regla) {
        if (regla.getId() == null) { em.persist(regla); return regla; }
        return em.merge(regla);
    }

    @Override
    public Optional<ReglaAdaptativa> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(ReglaAdaptativa.class, id))
                .filter(r -> !r.isEliminada());
    }

    @Override
    public Optional<ReglaAdaptativa> buscarPorEvaluacion(UUID idEvaluacion) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT r FROM ReglaAdaptativa r "
                            + "WHERE r.idEvaluacion = :idEvaluacion "
                            + "AND r.eliminadoEn IS NULL "
                            + "AND r.activa = true",
                            ReglaAdaptativa.class)
                    .setParameter("idEvaluacion", idEvaluacion)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ReglaAdaptativa> buscarPorCurso(UUID idCurso) {
        return em.createQuery(
                "SELECT r FROM ReglaAdaptativa r "
                + "WHERE r.idCurso = :idCurso "
                + "AND r.eliminadoEn IS NULL "
                + "AND r.activa = true",
                ReglaAdaptativa.class)
                .setParameter("idCurso", idCurso)
                .getResultList();
    }
}
