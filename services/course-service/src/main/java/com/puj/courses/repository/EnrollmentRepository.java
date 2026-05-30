package com.puj.courses.repository;

import com.puj.courses.entity.Enrollment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link Enrollment}.
 *
 * <p>Centraliza todas las consultas JPQL relacionadas con inscripciones, incluyendo
 * consultas estadísticas usadas por el panel de administración y el dashboard del
 * instructor. Los métodos excluyen registros con borrado lógico salvo indicación
 * explícita en el nombre del método.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class EnrollmentRepository {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    /**
     * Persiste una nueva inscripción.
     *
     * @param  enrollment entidad a guardar; no debe ser {@code null}
     * @return la entidad gestionada tras la persistencia
     */
    public Enrollment save(Enrollment enrollment) {
        em.persist(enrollment);
        return enrollment;
    }

    /**
     * Fusiona los cambios de una inscripción existente con el contexto de persistencia.
     *
     * @param  enrollment entidad con los cambios a aplicar; no debe ser {@code null}
     * @return la entidad gestionada tras la fusión
     */
    public Enrollment merge(Enrollment enrollment) {
        return em.merge(enrollment);
    }

    /**
     * Busca la inscripción activa de un usuario en un curso específico.
     *
     * @param  userId   identificador del estudiante
     * @param  courseId identificador del curso
     * @return {@link Optional} con la inscripción si existe y no fue eliminada,
     *         o vacío en caso contrario
     */
    public Optional<Enrollment> findByUserAndCourse(UUID userId, UUID courseId) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT e FROM Enrollment e"
                            + " WHERE e.userId = :uid"
                            + "   AND e.course.id = :cid"
                            + "   AND e.deletedAt IS NULL",
                            Enrollment.class)
                    .setParameter("uid", userId)
                    .setParameter("cid", courseId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Devuelve todas las inscripciones activas de un usuario, ordenadas
     * por fecha de inscripción descendente.
     *
     * @param  userId identificador del estudiante
     * @return lista (posiblemente vacía) de inscripciones del estudiante
     */
    public List<Enrollment> findByUser(UUID userId) {
        return em.createQuery(
                        "SELECT e FROM Enrollment e"
                        + " WHERE e.userId = :uid AND e.deletedAt IS NULL"
                        + " ORDER BY e.enrolledAt DESC",
                        Enrollment.class)
                .setParameter("uid", userId)
                .getResultList();
    }

    /**
     * Verifica si un usuario ya está inscrito en un curso.
     *
     * @param  userId   identificador del estudiante
     * @param  courseId identificador del curso
     * @return {@code true} si existe una inscripción activa
     */
    public boolean isEnrolled(UUID userId, UUID courseId) {
        return findByUserAndCourse(userId, courseId).isPresent();
    }

    /**
     * Cuenta el número de inscripciones activas en un curso.
     *
     * @param  courseId identificador del curso
     * @return número de inscripciones activas (no borradas lógicamente)
     */
    public long countByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT COUNT(e) FROM Enrollment e"
                        + " WHERE e.course.id = :cid AND e.deletedAt IS NULL",
                        Long.class)
                .setParameter("cid", courseId)
                .getSingleResult();
    }

    /**
     * Calcula el promedio del porcentaje de progreso de todos los estudiantes
     * inscritos activamente en un curso.
     *
     * @param  courseId identificador del curso
     * @return promedio de progreso (0.0 si no hay inscripciones)
     */
    public double avgProgressPct(UUID courseId) {
        Double avg = em.createQuery(
                        "SELECT AVG(e.progressPct) FROM Enrollment e"
                        + " WHERE e.course.id = :cid AND e.deletedAt IS NULL",
                        Double.class)
                .setParameter("cid", courseId)
                .getSingleResult();
        return avg != null ? avg : 0.0;
    }

    /**
     * Devuelve las filas agregadas de progreso por (usuario, módulo) para un curso.
     *
     * <p>Cada elemento de la lista es un array con los siguientes componentes:
     * <ol>
     *   <li>{@code UUID}    userId</li>
     *   <li>{@code UUID}    moduleId</li>
     *   <li>{@code String}  moduleTitle</li>
     *   <li>{@code Integer} moduleOrderIndex</li>
     *   <li>{@code Instant} MAX(completedAt)</li>
     * </ol>
     *
     * <p>El llamador debe conservar, por cada usuario, únicamente la fila con el
     * {@code MAX(completedAt)} más reciente para determinar el módulo actual.
     *
     * @param  courseId identificador del curso
     * @return lista de filas de progreso agrupadas por (usuario, módulo)
     */
    public List<Object[]> latestModulePerUser(UUID courseId) {
        return em.createQuery(
                        "SELECT p.userId, l.module.id, l.module.title,"
                        + "       l.module.orderIndex, MAX(p.completedAt)"
                        + " FROM LessonProgress p JOIN p.lesson l"
                        + " WHERE l.module.course.id = :cid"
                        + "   AND l.module.deletedAt IS NULL"
                        + " GROUP BY p.userId, l.module.id,"
                        + "          l.module.title, l.module.orderIndex",
                        Object[].class)
                .setParameter("cid", courseId)
                .getResultList();
    }

    /**
     * Cuenta los estudiantes únicos inscritos en al menos uno de los cursos indicados.
     *
     * @param  courseIds lista de identificadores de cursos; si es vacía devuelve 0
     * @return número de estudiantes únicos en el conjunto de cursos
     */
    public long countUniqueStudentsInCourses(List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return 0;
        return em.createQuery(
                        "SELECT COUNT(DISTINCT e.userId) FROM Enrollment e"
                        + " WHERE e.course.id IN :ids AND e.deletedAt IS NULL",
                        Long.class)
                .setParameter("ids", courseIds)
                .getSingleResult();
    }

    /**
     * Cuenta los estudiantes inscritos en un curso que aún no han completado
     * ninguna lección.
     *
     * @param  courseId identificador del curso
     * @return número de estudiantes sin progreso registrado
     */
    public long countNotStarted(UUID courseId) {
        return em.createQuery(
                        "SELECT COUNT(DISTINCT e.userId) FROM Enrollment e"
                        + " WHERE e.course.id = :cid AND e.deletedAt IS NULL"
                        + "   AND e.userId NOT IN ("
                        + "     SELECT DISTINCT p.userId FROM LessonProgress p"
                        + "     WHERE p.courseId = :cid"
                        + "   )",
                        Long.class)
                .setParameter("cid", courseId)
                .getSingleResult();
    }

    /**
     * Cuenta el total de inscripciones activas en la plataforma (cualquier estado,
     * excluyendo las eliminadas lógicamente).
     *
     * @return total de inscripciones activas
     */
    public long countAll() {
        return em.createQuery(
                        "SELECT COUNT(e) FROM Enrollment e WHERE e.deletedAt IS NULL",
                        Long.class)
                .getSingleResult();
    }

    /**
     * Cuenta el total de inscripciones con estado {@code COMPLETED} en la plataforma.
     *
     * @return total de inscripciones completadas
     */
    public long countCompleted() {
        return em.createQuery(
                        "SELECT COUNT(e) FROM Enrollment e"
                        + " WHERE e.status ="
                        + "       com.puj.courses.entity.EnrollmentStatus.COMPLETED"
                        + "   AND e.deletedAt IS NULL",
                        Long.class)
                .getSingleResult();
    }

    /**
     * Devuelve los {@code limit} cursos con mayor cantidad de inscripciones activas.
     *
     * <p>Cada elemento de la lista es un array {@code Object[]} con:
     * <ol>
     *   <li>{@code UUID}   courseId</li>
     *   <li>{@code String} courseTitle</li>
     *   <li>{@code Long}   enrollCount</li>
     * </ol>
     *
     * @param  limit número máximo de resultados
     * @return lista de cursos más populares por inscripciones
     */
    public List<Object[]> topPopularCourses(int limit) {
        return em.createQuery(
                        "SELECT e.course.id, e.course.title, COUNT(e)"
                        + " FROM Enrollment e"
                        + " WHERE e.deletedAt IS NULL"
                        + " GROUP BY e.course.id, e.course.title"
                        + " ORDER BY COUNT(e) DESC",
                        Object[].class)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Devuelve los {@code limit} cursos con mayor cantidad de inscripciones completadas.
     *
     * <p>Cada elemento de la lista es un array {@code Object[]} con:
     * <ol>
     *   <li>{@code UUID}   courseId</li>
     *   <li>{@code String} courseTitle</li>
     *   <li>{@code Long}   completedCount</li>
     * </ol>
     *
     * @param  limit número máximo de resultados
     * @return lista de cursos con más finalizaciones
     */
    public List<Object[]> topCompletedCourses(int limit) {
        return em.createQuery(
                        "SELECT e.course.id, e.course.title, COUNT(e)"
                        + " FROM Enrollment e"
                        + " WHERE e.status ="
                        + "       com.puj.courses.entity.EnrollmentStatus.COMPLETED"
                        + "   AND e.deletedAt IS NULL"
                        + " GROUP BY e.course.id, e.course.title"
                        + " ORDER BY COUNT(e) DESC",
                        Object[].class)
                .setMaxResults(limit)
                .getResultList();
    }
}
