package com.puj.assessments.repository;

import com.puj.assessments.entity.Assessment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AssessmentRepository {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    public Assessment save(Assessment a) {
        if (a.getId() == null) { em.persist(a); return a; }
        return em.merge(a);
    }

    public Optional<Assessment> findById(UUID id) {
        return Optional.ofNullable(em.find(Assessment.class, id))
                .filter(a -> !a.isDeleted());
    }

    public List<Assessment> findAll() {
        return em.createQuery(
                        "SELECT a FROM Assessment a WHERE a.deletedAt IS NULL ORDER BY a.createdAt",
                        Assessment.class)
                .getResultList();
    }

    public List<Assessment> findByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT a FROM Assessment a WHERE a.courseId = :cid AND a.deletedAt IS NULL ORDER BY a.createdAt",
                        Assessment.class)
                .setParameter("cid", courseId)
                .getResultList();
    }

    public List<Assessment> findByInstructor(UUID instructorId) {
        return em.createQuery(
                        "SELECT a FROM Assessment a WHERE a.instructorId = :iid AND a.deletedAt IS NULL ORDER BY a.createdAt",
                        Assessment.class)
                .setParameter("iid", instructorId)
                .getResultList();
    }

    public long countAttempts(UUID userId, UUID assessmentId) {
        return em.createQuery(
                        "SELECT COUNT(s) FROM Submission s WHERE s.userId = :uid AND s.assessment.id = :aid" +
                        " AND s.status IN (com.puj.assessments.entity.SubmissionStatus.SUBMITTED, com.puj.assessments.entity.SubmissionStatus.GRADED)",
                        Long.class)
                .setParameter("uid", userId)
                .setParameter("aid", assessmentId)
                .getSingleResult();
    }
}
