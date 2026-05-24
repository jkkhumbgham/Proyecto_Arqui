package com.puj.assessments.rest;

import com.puj.assessments.dto.AssessmentCreateRequest;
import com.puj.assessments.dto.AssessmentDetail;
import com.puj.assessments.dto.AssessmentSummary;
import com.puj.assessments.dto.AssessmentUpdateRequest;
import com.puj.assessments.entity.*;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/assessments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Evaluaciones")
public class AssessmentResource {

    @Inject private SubmissionService    submissionService;
    @Inject private AssessmentRepository assessmentRepo;
    @Inject private SubmissionRepository submissionRepo;
    @Inject private AuthenticatedUser    authenticatedUser;

    @GET
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Listar todas las evaluaciones activas")
    public Response findAll() {
        List<AssessmentSummary> summaries = assessmentRepo.findAll()
                .stream()
                .map(AssessmentSummary::from)
                .toList();
        return Response.ok(summaries).build();
    }

    @GET
    @Path("/my")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Mis evaluaciones (INSTRUCTOR)")
    public Response myAssessments() {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        List<AssessmentSummary> summaries = assessmentRepo.findByInstructor(instructorId)
                .stream().map(AssessmentSummary::from).toList();
        return Response.ok(summaries).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Obtener evaluación por ID (sin revelar respuestas correctas)")
    public Response findById(@PathParam("id") UUID assessmentId) {
        return assessmentRepo.findById(assessmentId)
                .map(a -> Response.ok(AssessmentDetail.from(a)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada: " + assessmentId + "\"}")
                        .build());
    }

    @GET
    @Path("/course/{courseId}")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Listar evaluaciones de un curso (resumen, sin preguntas)")
    public Response findByCourse(@PathParam("courseId") UUID courseId) {
        List<AssessmentSummary> summaries = assessmentRepo.findByCourse(courseId)
                .stream()
                .map(AssessmentSummary::from)
                .toList();
        return Response.ok(summaries).build();
    }

    @GET
    @Path("/{id}/submissions")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Resultados de estudiantes para una evaluación (INSTRUCTOR)")
    public Response getSubmissions(@PathParam("id") UUID assessmentId) {
        Assessment assessment = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Evaluación no encontrada"));
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!assessment.getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de esta evaluación\"}").build();
        }
        List<Map<String, Object>> results = submissionRepo.findByAssessment(assessmentId)
                .stream().map(s -> {
                    double pct = s.getMaxScore() != null && s.getMaxScore().doubleValue() > 0
                            ? (s.getScore().doubleValue() / s.getMaxScore().doubleValue()) * 100.0
                            : 0.0;
                    return Map.<String, Object>of(
                            "submissionId", s.getId(),
                            "userId",       s.getUserId(),
                            "score",        s.getScore() != null ? s.getScore() : BigDecimal.ZERO,
                            "maxScore",     s.getMaxScore() != null ? s.getMaxScore() : BigDecimal.ZERO,
                            "scorePct",     Math.round(pct * 10.0) / 10.0,
                            "passed",       s.isPassed(),
                            "attempt",      s.getAttemptNumber(),
                            "submittedAt",  s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : ""
                    );
                }).toList();
        return Response.ok(Map.of(
                "assessmentId",    assessmentId,
                "assessmentTitle", assessment.getTitle(),
                "submissions",     results
        )).build();
    }

    @POST
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Crear evaluación con preguntas (INSTRUCTOR)")
    public Response create(@Valid AssessmentCreateRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());

        Assessment assessment = new Assessment();
        assessment.setTitle(req.title());
        assessment.setCourseId(req.courseId());
        assessment.setLessonId(req.lessonId());
        assessment.setDescription(req.description());
        assessment.setInstructorId(instructorId);
        if (req.passingScorePct() != null) assessment.setPassingScorePct(req.passingScorePct());
        if (req.maxAttempts() != null)     assessment.setMaxAttempts(req.maxAttempts());

        List<Question> questions = new ArrayList<>();
        if (req.questions() != null) {
            int qIdx = 0;
            for (AssessmentCreateRequest.QuestionRequest qr : req.questions()) {
                Question q = new Question();
                q.setAssessment(assessment);
                q.setText(qr.text());
                q.setType(QuestionType.valueOf(qr.type()));
                q.setPoints(qr.points() > 0 ? qr.points() : 1.0);
                q.setOrderIndex(++qIdx);

                if (qr.options() != null) {
                    int oIdx = 0;
                    for (AssessmentCreateRequest.OptionRequest or : qr.options()) {
                        AnswerOption opt = new AnswerOption();
                        opt.setQuestion(q);
                        opt.setText(or.text());
                        opt.setCorrect(or.correct());
                        opt.setOrderIndex(++oIdx);
                        q.getOptions().add(opt);
                    }
                }
                questions.add(q);
            }
        }
        assessment.getQuestions().addAll(questions);
        assessmentRepo.save(assessment);

        return Response.status(Response.Status.CREATED)
                .entity(AssessmentDetail.from(assessment)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar evaluación (INSTRUCTOR)")
    public Response update(@PathParam("id") UUID assessmentId, AssessmentUpdateRequest req) {
        return assessmentRepo.findById(assessmentId)
                .map(a -> {
                    UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
                    if (!a.getInstructorId().equals(instructorId)) {
                        return Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"message\":\"No eres el instructor de esta evaluación\"}").build();
                    }
                    if (req.title() != null && !req.title().isBlank()) a.setTitle(req.title());
                    if (req.description() != null) a.setDescription(req.description());
                    a.setLessonId(req.lessonId());
                    if (req.passingScorePct() != null) a.setPassingScorePct(req.passingScorePct());
                    if (req.maxAttempts() != null)     a.setMaxAttempts(req.maxAttempts());

                    if (req.questions() != null) {
                        a.getQuestions().clear();
                        int qIdx = 0;
                        for (AssessmentCreateRequest.QuestionRequest qr : req.questions()) {
                            Question q = new Question();
                            q.setAssessment(a);
                            q.setText(qr.text());
                            q.setType(QuestionType.valueOf(qr.type()));
                            q.setPoints(qr.points() > 0 ? qr.points() : 1.0);
                            q.setOrderIndex(++qIdx);
                            if (qr.options() != null) {
                                int oIdx = 0;
                                for (AssessmentCreateRequest.OptionRequest or : qr.options()) {
                                    AnswerOption opt = new AnswerOption();
                                    opt.setQuestion(q);
                                    opt.setText(or.text());
                                    opt.setCorrect(or.correct());
                                    opt.setOrderIndex(++oIdx);
                                    q.getOptions().add(opt);
                                }
                            }
                            a.getQuestions().add(q);
                        }
                    }
                    assessmentRepo.save(a);
                    return Response.ok(AssessmentDetail.from(a)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada\"}").build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar evaluación (soft delete)")
    public Response delete(@PathParam("id") UUID assessmentId) {
        return assessmentRepo.findById(assessmentId)
                .map(a -> {
                    UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
                    if (!a.getInstructorId().equals(instructorId)) {
                        return Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"message\":\"No eres el instructor de esta evaluación\"}").build();
                    }
                    a.softDelete();
                    assessmentRepo.save(a);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada\"}").build());
    }

    @POST
    @Path("/{id}/submit")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Enviar respuestas de una evaluación")
    public Response submit(@PathParam("id") UUID assessmentId,
                           @Valid com.puj.assessments.dto.SubmitRequest req) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        com.puj.assessments.dto.SubmissionResult result = submissionService.submit(userId, assessmentId, req);
        return Response.ok(result).build();
    }
}
