package com.puj.assessments.repository;

import com.puj.assessments.entity.Submission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link Submission}.
 *
 * <p>Gestiona la persistencia de los intentos de evaluación de los estudiantes.
 * Los métodos de consulta filtran por estado cuando corresponde, excluyendo
 * submissions en estado {@code IN_PROGRESS} de los listados de completados.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class SubmissionRepository {

    @PersistenceContext(unitName = "AssessmentServicePU")
    private EntityManager em;

    /**
     * Persiste una nueva submission (INSERT).
     *
     * @param s submission nueva a persistir
     * @return la misma instancia administrada por JPA
     */
    public Submission save(Submission s) {
        em.persist(s);
        return s;
    }

    /**
     * Actualiza una submission existente (UPDATE/merge).
     *
     * @param s submission a actualizar
     * @return la instancia administrada resultante del merge
     */
    public Submission merge(Submission s) {
        return em.merge(s);
    }

    /**
     * Busca una submission por su identificador primario.
     *
     * @param id UUID de la submission
     * @return {@link Optional} con la submission, o vacío si no existe
     */
    public Optional<Submission> findById(UUID id) {
        return Optional.ofNullable(em.find(Submission.class, id));
    }

    /**
     * Retorna todos los intentos de un estudiante en una evaluación, ordenados
     * por fecha de inicio descendente (más reciente primero).
     *
     * @param userId       UUID del estudiante
     * @param assessmentId UUID de la evaluación
     * @return lista de submissions del estudiante en la evaluación (puede estar vacía)
     */
    public List<Submission> findByUserAndAssessment(UUID userId, UUID assessmentId) {
        return em.createQuery(
                "SELECT s FROM Submission s "
                + "WHERE s.userId = :uid "
                + "AND s.assessment.id = :aid "
                + "ORDER BY s.startedAt DESC",
                Submission.class)
                .setParameter("uid", userId)
                .setParameter("aid", assessmentId)
                .getResultList();
    }

    /**
     * Retorna los intentos de un estudiante paginados, ordenados por fecha
     * de inicio descendente.
     *
     * @param userId UUID del estudiante
     * @param page   número de página (base 0)
     * @param size   tamaño de página
     * @return lista paginada de submissions del estudiante
     */
    public List<Submission> findByUser(UUID userId, int page, int size) {
        return em.createQuery(
                "SELECT s FROM Submission s "
                + "WHERE s.userId = :uid "
                + "ORDER BY s.startedAt DESC",
                Submission.class)
                .setParameter("uid", userId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /**
     * Retorna los intentos completados o calificados de una evaluación, ordenados
     * por fecha de envío descendente.
     *
     * @param assessmentId UUID de la evaluación
     * @return lista de submissions en estado SUBMITTED o GRADED
     */
    public List<Submission> findByAssessment(UUID assessmentId) {
        return em.createQuery(
                "SELECT s FROM Submission s "
                + "WHERE s.assessment.id = :aid "
                + "AND s.status IN ("
                + "  com.puj.assessments.entity.SubmissionStatus.SUBMITTED, "
                + "  com.puj.assessments.entity.SubmissionStatus.GRADED"
                + ") ORDER BY s.submittedAt DESC",
                Submission.class)
                .setParameter("aid", assessmentId)
                .getResultList();
    }

    /**
     * Retorna los intentos completados de un estudiante, paginados y ordenados
     * por fecha de envío descendente.
     *
     * @param userId UUID del estudiante
     * @param page   número de página (base 0)
     * @param size   tamaño de página
     * @return lista paginada de submissions en estado SUBMITTED o GRADED
     */
    public List<Submission> findCompletedByUser(UUID userId, int page, int size) {
        return em.createQuery(
                "SELECT s FROM Submission s "
                + "WHERE s.userId = :uid "
                + "AND s.status IN ("
                + "  com.puj.assessments.entity.SubmissionStatus.SUBMITTED, "
                + "  com.puj.assessments.entity.SubmissionStatus.GRADED"
                + ") ORDER BY s.submittedAt DESC",
                Submission.class)
                .setParameter("uid", userId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /**
     * Retorna los intentos completados de un estudiante en un conjunto de evaluaciones.
     *
     * <p>Utilizado para calcular el promedio de puntaje del estudiante en el contexto
     * del bloqueo de módulos del curso.</p>
     *
     * @param userId        UUID del estudiante
     * @param assessmentIds lista de UUIDs de las evaluaciones a consultar
     * @return lista de submissions completadas en las evaluaciones indicadas,
     *         o lista vacía si {@code assessmentIds} es nulo o vacío
     */
    public List<Submission> findByUserAndAssessments(UUID userId, List<UUID> assessmentIds) {
        if (assessmentIds == null || assessmentIds.isEmpty()) return List.of();
        return em.createQuery(
                "SELECT s FROM Submission s "
                + "WHERE s.userId = :uid "
                + "AND s.assessment.id IN :aids "
                + "AND s.status IN ("
                + "  com.puj.assessments.entity.SubmissionStatus.SUBMITTED, "
                + "  com.puj.assessments.entity.SubmissionStatus.GRADED"
                + ")",
                Submission.class)
                .setParameter("uid", userId)
                .setParameter("aids", assessmentIds)
                .getResultList();
    }
}
