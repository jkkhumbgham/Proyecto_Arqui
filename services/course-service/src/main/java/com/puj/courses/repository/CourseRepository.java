package com.puj.courses.repository;

import com.puj.courses.entity.Course;
import com.puj.courses.entity.CourseStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CourseRepository {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    public Course save(Course course) {
        if (course.getId() == null) {
            em.persist(course);
            return course;
        }
        return em.merge(course);
    }

    public Optional<Course> findById(UUID id) {
        return Optional.ofNullable(em.find(Course.class, id))
                .filter(c -> !c.isDeleted());
    }

    public List<Course> findPublished(int page, int size) {
        return em.createQuery(
                        "SELECT c FROM Course c WHERE c.status = :status AND c.deletedAt IS NULL ORDER BY c.createdAt DESC",
                        Course.class)
                .setParameter("status", CourseStatus.PUBLISHED)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public List<Course> findByInstructor(UUID instructorId, int page, int size) {
        return em.createQuery(
                        "SELECT c FROM Course c WHERE c.instructorId = :iid AND c.deletedAt IS NULL ORDER BY c.createdAt DESC",
                        Course.class)
                .setParameter("iid", instructorId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public long countPublished() {
        return em.createQuery(
                        "SELECT COUNT(c) FROM Course c WHERE c.status = :status AND c.deletedAt IS NULL",
                        Long.class)
                .setParameter("status", CourseStatus.PUBLISHED)
                .getSingleResult();
    }

    public boolean isOwner(UUID courseId, UUID instructorId) {
        try {
            em.createQuery(
                            "SELECT c.id FROM Course c WHERE c.id = :id AND c.instructorId = :iid AND c.deletedAt IS NULL",
                            UUID.class)
                    .setParameter("id", courseId)
                    .setParameter("iid", instructorId)
                    .getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }
}
