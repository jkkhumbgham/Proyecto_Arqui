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

    public double avgProgressPct(UUID courseId) {
        Double avg = em.createQuery(
                        "SELECT AVG(e.progressPct) FROM Enrollment e WHERE e.course.id = :cid AND e.deletedAt IS NULL",
                        Double.class)
                .setParameter("cid", courseId)
                .getSingleResult();
        return avg != null ? avg : 0.0;
    }

    /**
     * Returns [userId, moduleId, moduleTitle, moduleOrderIndex, MAX(completedAt)]
     * per (user, module) combination. The caller picks the max-completedAt row
     * per user to determine each student's current module.
     */
    public List<Object[]> latestModulePerUser(UUID courseId) {
        return em.createQuery(
                        "SELECT p.userId, l.module.id, l.module.title, l.module.orderIndex, MAX(p.completedAt)" +
                        " FROM LessonProgress p JOIN p.lesson l" +
                        " WHERE l.module.course.id = :cid AND l.module.deletedAt IS NULL" +
                        " GROUP BY p.userId, l.module.id, l.module.title, l.module.orderIndex",
                        Object[].class)
                .setParameter("cid", courseId)
                .getResultList();
    }

    public long countUniqueStudentsInCourses(List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return 0;
        return em.createQuery(
                        "SELECT COUNT(DISTINCT e.userId) FROM Enrollment e" +
                        " WHERE e.course.id IN :ids AND e.deletedAt IS NULL",
                        Long.class)
                .setParameter("ids", courseIds)
                .getSingleResult();
    }

    public long countNotStarted(UUID courseId) {
        return em.createQuery(
                        "SELECT COUNT(DISTINCT e.userId) FROM Enrollment e" +
                        " WHERE e.course.id = :cid AND e.deletedAt IS NULL" +
                        " AND e.userId NOT IN (" +
                        "   SELECT DISTINCT p.userId FROM LessonProgress p WHERE p.courseId = :cid" +
                        " )",
                        Long.class)
                .setParameter("cid", courseId)
                .getSingleResult();
    }
}
