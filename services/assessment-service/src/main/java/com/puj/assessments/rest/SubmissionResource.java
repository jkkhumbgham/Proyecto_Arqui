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

@Path("/submissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Submissions")
public class SubmissionResource {

    @Inject private SubmissionService    submissionService;
    @Inject private SubmissionRepository submissionRepo;
    @Inject private AssessmentRepository assessmentRepo;
    @Inject private AuthenticatedUser    authenticatedUser;

    public record StartRequest(String assessmentId) {}

    /**
     * Crea una submission en estado IN_PROGRESS.
     * El frontend llama esto antes de mostrar las preguntas.
     */
    @POST
    @RequiresRole(Role.STUDENT)
    @Transactional
    @Operation(summary = "Iniciar una submission (STUDENT)")
    public Response start(StartRequest req) {
        UUID assessmentId = UUID.fromString(req.assessmentId());
        UUID userId = UUID.fromString(authenticatedUser.getUserId());

        Assessment assessment = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Evaluación no encontrada: " + assessmentId));

        long attempts = assessmentRepo.countAttempts(userId, assessmentId);
        if (attempts >= assessment.getMaxAttempts()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Has alcanzado el número máximo de intentos (" + assessment.getMaxAttempts() + ")."))
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

    /** Historial de evaluaciones completadas del estudiante autenticado. */
    @GET
    @Path("/my")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Historial de submissions del estudiante (STUDENT)")
    public Response mySubmissions(@QueryParam("page") @DefaultValue("0") int page,
                                  @QueryParam("size") @DefaultValue("50") int size) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        List<Submission> subs = submissionRepo.findCompletedByUser(userId, page, size);

        List<Map<String, Object>> result = subs.stream().map(s -> {
            double pct = scorePct(s);
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
            m.put("submittedAt",     s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    /** Promedio del mejor intento por assessment para un usuario — usado para bloqueo de módulos. */
    @GET
    @Path("/avg-for-assessments")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Avg score para un conjunto de assessments (módulo locking)")
    public Response avgForAssessments(@QueryParam("userId")        String userIdStr,
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

        List<Submission> allSubs = submissionRepo.findByUserAndAssessments(userId, assessmentIds);

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

    /** Métricas de evaluaciones de un curso — para instructores. */
    @GET
    @Path("/course-metrics/{courseId}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Métricas por assessment de un curso (INSTRUCTOR/ADMIN)")
    public Response courseMetrics(@PathParam("courseId") UUID courseId) {
        List<Assessment> assessments = assessmentRepo.findByCourse(courseId);

        List<Map<String, Object>> result = assessments.stream().map(a -> {
            List<Submission> subs = submissionRepo.findByAssessment(a.getId());
            long passCount = subs.stream().filter(Submission::isPassed).count();
            double avgPct  = subs.isEmpty() ? 0.0 :
                    subs.stream().mapToDouble(this::scorePct).average().orElse(0.0);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assessmentId",    a.getId().toString());
            m.put("assessmentTitle", a.getTitle());
            m.put("submissionCount", subs.size());
            m.put("avgScorePct",     round1(avgPct));
            m.put("passRate",        subs.isEmpty() ? 0.0 : round1((double) passCount / subs.size() * 100.0));
            return m;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    private double scorePct(Submission s) {
        if (s.getScore() == null || s.getMaxScore() == null
                || s.getMaxScore().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return s.getScore().doubleValue() / s.getMaxScore().doubleValue() * 100.0;
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /**
     * Envía y califica las respuestas de una submission existente.
     */
    @POST
    @Path("/{id}/submit")
    @RequiresRole(Role.STUDENT)
    @Transactional
    @Operation(summary = "Enviar y calificar respuestas (STUDENT)")
    public Response submit(@PathParam("id") UUID submissionId,
                           @Valid SubmitRequest req) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());

        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission no encontrada: " + submissionId));

        if (!submission.getUserId().equals(userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("message", "No tienes permiso para enviar esta submission."))
                    .build();
        }

        SubmissionResult result = submissionService.gradeExisting(submission, req);

        return Response.ok(result).build();
    }
}
