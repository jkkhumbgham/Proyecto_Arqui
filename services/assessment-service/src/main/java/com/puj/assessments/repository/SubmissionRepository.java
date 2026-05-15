package com.puj.assessments.repository;

import com.puj.assessments.entity.Submission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SubmissionRepository {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    public Submission save(Submission s) {
        em.persist(s);
        return s;
    }

    public Submission merge(Submission s) {
        return em.merge(s);
    }

    public Optional<Submission> findById(UUID id) {
        return Optional.ofNullable(em.find(Submission.class, id));
    }

    public List<Submission> findByUserAndAssessment(UUID userId, UUID assessmentId) {
        return em.createQuery(
                        "SELECT s FROM Submission s WHERE s.userId = :uid AND s.assessment.id = :aid ORDER BY s.startedAt DESC",
                        Submission.class)
                .setParameter("uid", userId)
                .setParameter("aid", assessmentId)
                .getResultList();
    }

    public List<Submission> findByUser(UUID userId, int page, int size) {
        return em.createQuery(
                        "SELECT s FROM Submission s WHERE s.userId = :uid ORDER BY s.startedAt DESC",
                        Submission.class)
                .setParameter("uid", userId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
