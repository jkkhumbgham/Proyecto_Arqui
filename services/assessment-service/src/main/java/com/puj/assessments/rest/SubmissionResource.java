package com.puj.assessments.rest;

import com.puj.assessments.dto.SubmitRequest;
import com.puj.assessments.dto.SubmissionResult;
import com.puj.assessments.entity.Assessment;
import com.puj.assessments.entity.Submission;
import com.puj.assessments.entity.SubmissionStatus;
import com.puj.assessments.repository.AssessmentRepository;
import com.puj.assessments.repository.SubmissionRepository;
import com.puj.assessments.service.SubmissionService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recurso REST para la gestión de submissions (intentos de evaluación).
 *
 * <p>Cubre el ciclo completo de un intento: inicio ({@code IN_PROGRESS}),
 * envío de respuestas ({@code GRADED}) e historial. También expone endpoints
 * para métricas de curso utilizados por el módulo de bloqueo de contenido.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/submissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Submissions")
public class SubmissionResource {

    @Inject private SubmissionService    submissionService;
    @Inject private SubmissionRepository submissionRepo;
    @Inject private AssessmentRepository assessmentRepo;
    @Inject private AuthenticatedUser    authenticatedUser;

    /**
     * DTO interno para iniciar una submission.
     */
    public record StartRequest(String assessmentId) {}

    /**
     * Inicia una nueva submission en estado {@code IN_PROGRESS}.
     *
     * <p>El frontend debe llamar este endpoint antes de mostrar las preguntas.
     * Verifica que el estudiante no haya alcanzado el límite de intentos.</p>
     *
     * @param req cuerpo con el identificador de la evaluación como {@code String}
     * @return respuesta 201 con el UUID de la submission creada,
     *         o 400 si se alcanzó el límite de intentos
     * @throws NotFoundException si la evaluación no existe
     */
    @POST
    @RequiresRole(Role.STUDENT)
    @Transactional
    @Operation(summary = "Iniciar una submission (STUDENT)")
    public Response start(StartRequest req) {
        UUID assessmentId = UUID.fromString(req.assessmentId());
        UUID userId = UUID.fromString(authenticatedUser.getUserId());

        Assessment assessment = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException(
                        "Evaluación no encontrada: " + assessmentId));

        long attempts = assessmentRepo.countAttempts(userId, assessmentId);
        if (attempts >= assessment.getMaxAttempts()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message",
                            "Has alcanzado el número máximo de intentos ("
                            + assessment.getMaxAttempts() + ")."))
                    .build();
        }

        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setAssessment(assessment);
        submission.setAttemptNumber((int) attempts + 1);
        submission.setStatus(SubmissionStatus.IN_PROGRESS);
        submissionRepo.save(submission);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", submission.getId().toString()))
                .build();
    }

    /**
     * Retorna el historial de evaluaciones completadas del estudiante autenticado.
     *
     * @param page número de página (base 0, por defecto 0)
     * @param size tamaño de página (por defecto 50)
     * @return respuesta 200 con la lista paginada de submissions completadas
     */
    @GET
    @Path("/my")
    @RequiresRole(Role.STUDENT)
    @Transactional
    @Operation(summary = "Historial de submissions del estudiante (STUDENT)")
    public Response mySubmissions(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {

        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        List<Submission> subs = submissionRepo.findCompletedByUser(userId, page, size);

        List<Map<String, Object>> result = subs.stream()
                .map(s -> buildSubmissionRow(s, scorePct(s)))
                .collect(Collectors.toList());

        return Response.ok(result).build();
    }

    /**
     * Calcula el promedio del mejor intento por evaluación de un usuario.
     *
     * <p>Utilizado por el servicio de cursos para determinar el bloqueo de módulos.
     * Si {@code userId} no se proporciona, se usa el usuario autenticado.</p>
     *
     * @param userIdStr        UUID del usuario como {@code String}, o {@code null}
     *                         para usar el usuario autenticado
     * @param assessmentIdsStr UUIDs de evaluaciones separados por coma
     * @return respuesta 200 con el mapa {@code {avgScorePct: double}}
     */
    @GET
    @Path("/avg-for-assessments")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Avg score para un conjunto de assessments (módulo locking)")
    public Response avgForAssessments(
            @QueryParam("userId")        String userIdStr,
            @QueryParam("assessmentIds") String assessmentIdsStr) {

        if (assessmentIdsStr == null || assessmentIdsStr.isBlank()) {
            return Response.ok(Map.of("avgScorePct", 0.0)).build();
        }

        UUID userId = (userIdStr != null && !userIdStr.isBlank())
                ? UUID.fromString(userIdStr)
                : UUID.fromString(authenticatedUser.getUserId());

        List<UUID> assessmentIds = Arrays.stream(assessmentIdsStr.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .map(UUID::fromString).collect(Collectors.toList());

        List<Submission> allSubs =
                submissionRepo.findByUserAndAssessments(userId, assessmentIds);

        Map<UUID, Double> bestPerAssessment = new HashMap<>();
        for (Submission s : allSubs) {
            UUID aid = s.getAssessment().getId();
            bestPerAssessment.merge(aid, scorePct(s), Math::max);
        }

        double avg = assessmentIds.stream()
                .mapToDouble(aid -> bestPerAssessment.getOrDefault(aid, 0.0))
                .average().orElse(0.0);

        return Response.ok(Map.of("avgScorePct", round1(avg))).build();
    }

    /**
     * Retorna métricas agregadas de todas las evaluaciones de un curso.
     *
     * <p>Para cada evaluación incluye el número de submissions, el promedio
     * de puntaje y la tasa de aprobación.</p>
     *
     * @param courseId UUID del curso
     * @return respuesta 200 con la lista de métricas por evaluación
     */
    @GET
    @Path("/course-metrics/{courseId}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Métricas por assessment de un curso (INSTRUCTOR/ADMIN)")
    public Response courseMetrics(@PathParam("courseId") UUID courseId) {
        List<Assessment> assessments = assessmentRepo.findByCourse(courseId);

        List<Map<String, Object>> result = assessments.stream()
                .map(a -> buildCourseMetricRow(a))
                .collect(Collectors.toList());

        return Response.ok(result).build();
    }

    /**
     * Envía y califica las respuestas de una submission existente (en estado
     * {@code IN_PROGRESS}).
     *
     * <p>Verifica que la submission pertenezca al usuario autenticado antes de
     * delegar la calificación en {@link SubmissionService}.</p>
     *
     * @param submissionId UUID de la submission a calificar
     * @param req          respuestas y duración del intento
     * @return respuesta 200 con el {@link SubmissionResult} de la calificación,
     *         o 403 si la submission no pertenece al usuario
     * @throws NotFoundException si la submission no existe
     */
    @POST
    @Path("/{id}/submit")
    @RequiresRole(Role.STUDENT)
    @Transactional
    @Operation(summary = "Enviar y calificar respuestas (STUDENT)")
    public Response submit(
            @PathParam("id") UUID submissionId,
            @Valid SubmitRequest req) {

        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new NotFoundException(
                        "Submission no encontrada: " + submissionId));

        if (!submission.getUserId().equals(userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("message",
                            "No tienes permiso para enviar esta submission."))
                    .build();
        }

        SubmissionResult result = submissionService.gradeExisting(submission, req);
        return Response.ok(result).build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Calcula el porcentaje de puntaje de una submission.
     *
     * @param s submission calificada
     * @return porcentaje (0-100), o 0.0 si el puntaje máximo es nulo o cero
     */
    private double scorePct(Submission s) {
        if (s.getScore() == null || s.getMaxScore() == null
                || s.getMaxScore().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return s.getScore().doubleValue() / s.getMaxScore().doubleValue() * 100.0;
    }

    /**
     * Redondea un valor a un decimal.
     *
     * @param v valor a redondear
     * @return valor redondeado a 1 decimal
     */
    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /**
     * Construye el mapa de datos de una fila del historial de submissions
     * del estudiante.
     *
     * @param s   submission a proyectar
     * @param pct porcentaje de puntaje ya calculado
     * @return mapa con los campos de la submission
     */
    private Map<String, Object> buildSubmissionRow(Submission s, double pct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("submissionId",    s.getId().toString());
        m.put("assessmentId",    s.getAssessment().getId().toString());
        m.put("assessmentTitle", s.getAssessment().getTitle());
        m.put("courseId",        s.getAssessment().getCourseId().toString());
        m.put("score",           s.getScore());
        m.put("maxScore",        s.getMaxScore());
        m.put("scorePct",        round1(pct));
        m.put("passed",          s.isPassed());
        m.put("attemptNumber",   s.getAttemptNumber());
        m.put("submittedAt",
                s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : null);
        return m;
    }

    /**
     * Construye el mapa de métricas para una evaluación dentro de un curso.
     *
     * @param a evaluación a proyectar
     * @return mapa con las métricas de la evaluación
     */
    private Map<String, Object> buildCourseMetricRow(Assessment a) {
        List<Submission> subs = submissionRepo.findByAssessment(a.getId());
        long passCount = subs.stream().filter(Submission::isPassed).count();
        double avgPct  = subs.isEmpty() ? 0.0
                : subs.stream().mapToDouble(this::scorePct).average().orElse(0.0);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessmentId",    a.getId().toString());
        m.put("assessmentTitle", a.getTitle());
        m.put("submissionCount", subs.size());
        m.put("avgScorePct",     round1(avgPct));
        m.put("passRate",        subs.isEmpty() ? 0.0
                : round1((double) passCount / subs.size() * 100.0));
        return m;
    }
}
