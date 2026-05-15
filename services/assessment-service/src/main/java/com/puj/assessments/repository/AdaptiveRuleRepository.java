package com.puj.assessments.repository;

import com.puj.assessments.entity.AdaptiveRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AdaptiveRuleRepository {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    public AdaptiveRule save(AdaptiveRule rule) {
        if (rule.getId() == null) { em.persist(rule); return rule; }
        return em.merge(rule);
    }

    public Optional<AdaptiveRule> findById(UUID id) {
        return Optional.ofNullable(em.find(AdaptiveRule.class, id))
                .filter(r -> !r.isDeleted());
    }

    public Optional<AdaptiveRule> findByAssessment(UUID assessmentId) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT r FROM AdaptiveRule r WHERE r.assessmentId = :aid AND r.deletedAt IS NULL AND r.active = true",
                            AdaptiveRule.class)
                    .setParameter("aid", assessmentId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
