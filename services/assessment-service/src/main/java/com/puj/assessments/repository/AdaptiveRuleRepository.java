package com.puj.assessments.repository;

import com.puj.assessments.entity.AdaptiveRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link AdaptiveRule}.
 *
 * <p>Todas las consultas excluyen reglas eliminadas lógicamente
 * ({@code deletedAt IS NULL}) y en las búsquedas activas también
 * se filtra por {@code active = true}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class AdaptiveRuleRepository {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    /**
     * Persiste o actualiza una regla adaptativa.
     *
     * @param rule regla a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    public AdaptiveRule save(AdaptiveRule rule) {
        if (rule.getId() == null) { em.persist(rule); return rule; }
        return em.merge(rule);
    }

    /**
     * Busca una regla por su identificador primario, excluyendo eliminadas.
     *
     * @param id UUID de la regla
     * @return {@link Optional} con la regla, o vacío si no existe o está eliminada
     */
    public Optional<AdaptiveRule> findById(UUID id) {
        return Optional.ofNullable(em.find(AdaptiveRule.class, id))
                .filter(r -> !r.isDeleted());
    }

    /**
     * Busca la regla activa asociada a una evaluación específica.
     *
     * @param assessmentId UUID de la evaluación
     * @return {@link Optional} con la regla activa, o vacío si no existe
     */
    public Optional<AdaptiveRule> findByAssessment(UUID assessmentId) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT r FROM AdaptiveRule r "
                            + "WHERE r.assessmentId = :aid "
                            + "AND r.deletedAt IS NULL "
                            + "AND r.active = true",
                            AdaptiveRule.class)
                    .setParameter("aid", assessmentId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Retorna todas las reglas activas de un curso.
     *
     * @param courseId UUID del curso
     * @return lista de reglas activas del curso (puede estar vacía)
     */
    public List<AdaptiveRule> findByCourse(UUID courseId) {
        return em.createQuery(
                "SELECT r FROM AdaptiveRule r "
                + "WHERE r.courseId = :cid "
                + "AND r.deletedAt IS NULL "
                + "AND r.active = true",
                AdaptiveRule.class)
                .setParameter("cid", courseId)
                .getResultList();
    }
}
