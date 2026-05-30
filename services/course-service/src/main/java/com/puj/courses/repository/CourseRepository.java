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

/**
 * Repositorio JPA para la entidad {@link Course}.
 *
 * <p>Encapsula todas las consultas JPQL relacionadas con cursos. Los métodos de
 * búsqueda excluyen automáticamente los registros con borrado lógico
 * ({@code deletedAt IS NOT NULL}).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class CourseRepository {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    /**
     * Persiste o actualiza un curso según si ya tiene identificador asignado.
     *
     * @param  course entidad a guardar; no debe ser {@code null}
     * @return la entidad gestionada tras la operación
     */
    public Course save(Course course) {
        if (course.getId() == null) {
            em.persist(course);
            return course;
        }
        return em.merge(course);
    }

    /**
     * Busca un curso por su identificador, excluyendo los que tienen borrado lógico.
     *
     * @param  id identificador del curso
     * @return {@link Optional} con el curso si existe y no fue eliminado,
     *         o vacío en caso contrario
     */
    public Optional<Course> findById(UUID id) {
        return Optional.ofNullable(em.find(Course.class, id))
                .filter(c -> !c.isDeleted());
    }

    /**
     * Devuelve la página indicada de cursos en estado {@code PUBLISHED},
     * ordenados por fecha de creación descendente.
     *
     * @param  page número de página (base 0)
     * @param  size cantidad máxima de resultados
     * @return lista (posiblemente vacía) de cursos publicados
     */
    public List<Course> findPublished(int page, int size) {
        return em.createQuery(
                        "SELECT c FROM Course c"
                        + " WHERE c.status = :status AND c.deletedAt IS NULL"
                        + " ORDER BY c.createdAt DESC",
                        Course.class)
                .setParameter("status", CourseStatus.PUBLISHED)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /**
     * Devuelve la página indicada de cursos pertenecientes a un instructor,
     * ordenados por fecha de creación descendente.
     *
     * @param  instructorId identificador del instructor propietario
     * @param  page         número de página (base 0)
     * @param  size         cantidad máxima de resultados
     * @return lista (posiblemente vacía) de cursos del instructor
     */
    public List<Course> findByInstructor(UUID instructorId, int page, int size) {
        return em.createQuery(
                        "SELECT c FROM Course c"
                        + " WHERE c.instructorId = :iid AND c.deletedAt IS NULL"
                        + " ORDER BY c.createdAt DESC",
                        Course.class)
                .setParameter("iid", instructorId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /**
     * Cuenta el total de cursos en estado {@code PUBLISHED} y sin borrado lógico.
     *
     * @return número de cursos publicados
     */
    public long countPublished() {
        return em.createQuery(
                        "SELECT COUNT(c) FROM Course c"
                        + " WHERE c.status = :status AND c.deletedAt IS NULL",
                        Long.class)
                .setParameter("status", CourseStatus.PUBLISHED)
                .getSingleResult();
    }

    /**
     * Verifica si un instructor es propietario de un curso determinado.
     *
     * @param  courseId     identificador del curso
     * @param  instructorId identificador del instructor
     * @return {@code true} si el instructor es propietario del curso activo
     */
    public boolean isOwner(UUID courseId, UUID instructorId) {
        try {
            em.createQuery(
                            "SELECT c.id FROM Course c"
                            + " WHERE c.id = :id"
                            + "   AND c.instructorId = :iid"
                            + "   AND c.deletedAt IS NULL",
                            UUID.class)
                    .setParameter("id",  courseId)
                    .setParameter("iid", instructorId)
                    .getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }
}
