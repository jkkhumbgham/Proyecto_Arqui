package com.puj.courses.repository;

import com.puj.courses.entity.Enrollment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EnrollmentRepository {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    public Enrollment save(Enrollment enrollment) {
        em.persist(enrollment);
        return enrollment;
    }

    public Enrollment merge(Enrollment enrollment) {
        return em.merge(enrollment);
    }

    public Optional<Enrollment> findByUserAndCourse(UUID userId, UUID courseId) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT e FROM Enrollment e WHERE e.userId = :uid AND e.course.id = :cid AND e.deletedAt IS NULL",
                            Enrollment.class)
                    .setParameter("uid", userId)
                    .setParameter("cid", courseId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public List<Enrollment> findByUser(UUID userId) {
        return em.createQuery(
                        "SELECT e FROM Enrollment e WHERE e.userId = :uid AND e.deletedAt IS NULL ORDER BY e.enrolledAt DESC",
                        Enrollment.class)
                .setParameter("uid", userId)
                .getResultList();
    }

    public boolean isEnrolled(UUID userId, UUID courseId) {
        return findByUserAndCourse(userId, courseId).isPresent();
    }

    public long countByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT COUNT(e) FROM Enrollment e WHERE e.course.id = :cid AND e.deletedAt IS NULL",
                        Long.class)
                .setParameter("cid", courseId)
                .getSingleResult();
    }
}
