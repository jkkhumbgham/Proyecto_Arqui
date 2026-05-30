package com.puj.assessments.repository;

import com.puj.assessments.entity.Assessment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link Assessment}.
 *
 * <p>Todas las consultas de listado excluyen evaluaciones eliminadas lógicamente
 * ({@code deletedAt IS NULL}) y ordenan por {@code createdAt} ascendente.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class AssessmentRepository {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    /**
     * Persiste o actualiza una evaluación.
     *
     * @param a evaluación a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    public Assessment save(Assessment a) {
        if (a.getId() == null) { em.persist(a); return a; }
        return em.merge(a);
    }

    /**
     * Busca una evaluación por su identificador primario, excluyendo eliminadas.
     *
     * @param id UUID de la evaluación
     * @return {@link Optional} con la evaluación, o vacío si no existe o está eliminada
     */
    public Optional<Assessment> findById(UUID id) {
        return Optional.ofNullable(em.find(Assessment.class, id))
                .filter(a -> !a.isDeleted());
    }

    /**
     * Retorna todas las evaluaciones activas ordenadas por fecha de creación.
     *
     * @return lista de evaluaciones activas (puede estar vacía)
     */
    public List<Assessment> findAll() {
        return em.createQuery(
                "SELECT a FROM Assessment a "
                + "WHERE a.deletedAt IS NULL "
                + "ORDER BY a.createdAt",
                Assessment.class)
                .getResultList();
    }

    /**
     * Retorna las evaluaciones activas de un curso ordenadas por fecha de creación.
     *
     * @param courseId UUID del curso
     * @return lista de evaluaciones del curso (puede estar vacía)
     */
    public List<Assessment> findByCourse(UUID courseId) {
        return em.createQuery(
                "SELECT a FROM Assessment a "
                + "WHERE a.courseId = :cid "
                + "AND a.deletedAt IS NULL "
                + "ORDER BY a.createdAt",
                Assessment.class)
                .setParameter("cid", courseId)
                .getResultList();
    }

    /**
     * Retorna las evaluaciones activas creadas por un instructor, ordenadas por
     * fecha de creación.
     *
     * @param instructorId UUID del instructor
     * @return lista de evaluaciones del instructor (puede estar vacía)
     */
    public List<Assessment> findByInstructor(UUID instructorId) {
        return em.createQuery(
                "SELECT a FROM Assessment a "
                + "WHERE a.instructorId = :iid "
                + "AND a.deletedAt IS NULL "
                + "ORDER BY a.createdAt",
                Assessment.class)
                .setParameter("iid", instructorId)
                .getResultList();
    }

    /**
     * Cuenta los intentos completados o calificados de un estudiante en una evaluación.
     *
     * <p>Se usa para verificar si el estudiante ha alcanzado el límite de intentos
     * antes de permitir una nueva submission.</p>
     *
     * @param userId       UUID del estudiante
     * @param assessmentId UUID de la evaluación
     * @return número de intentos en estado SUBMITTED o GRADED
     */
    public long countAttempts(UUID userId, UUID assessmentId) {
        return em.createQuery(
                "SELECT COUNT(s) FROM Submission s "
                + "WHERE s.userId = :uid "
                + "AND s.assessment.id = :aid "
                + "AND s.status IN ("
                + "  com.puj.assessments.entity.SubmissionStatus.SUBMITTED, "
                + "  com.puj.assessments.entity.SubmissionStatus.GRADED"
                + ")",
                Long.class)
                .setParameter("uid", userId)
                .setParameter("aid", assessmentId)
                .getSingleResult();
    }
}
