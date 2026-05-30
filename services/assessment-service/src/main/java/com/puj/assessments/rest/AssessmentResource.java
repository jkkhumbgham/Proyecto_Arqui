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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recurso REST para la gestión de evaluaciones.
 *
 * <p>Expone los endpoints CRUD sobre {@link Assessment} y delega la lógica de
 * calificación en {@link SubmissionService}. Los instructores pueden crear,
 * actualizar y eliminar evaluaciones; los estudiantes pueden consultarlas y
 * enviar sus respuestas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/assessments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Evaluaciones")
public class AssessmentResource {

    @Inject private SubmissionService    submissionService;
    @Inject private AssessmentRepository assessmentRepo;
    @Inject private SubmissionRepository submissionRepo;
    @Inject private AuthenticatedUser    authenticatedUser;

    /**
     * Lista todas las evaluaciones activas con su resumen (sin preguntas).
     *
     * @return respuesta 200 con la lista de {@link AssessmentSummary}
     */
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

    /**
     * Lista las evaluaciones creadas por el instructor autenticado.
     *
     * @return respuesta 200 con la lista de {@link AssessmentSummary} del instructor
     */
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

    /**
     * Obtiene el detalle completo de una evaluación por su identificador.
     *
     * @param assessmentId UUID de la evaluación a consultar
     * @return respuesta 200 con {@link AssessmentDetail}, o 404 si no existe
     */
    @GET
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Obtener evaluación por ID (sin revelar respuestas correctas)")
    public Response findById(@PathParam("id") UUID assessmentId) {
        return assessmentRepo.findById(assessmentId)
                .map(a -> Response.ok(AssessmentDetail.from(a)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada: "
                                + assessmentId + "\"}")
                        .build());
    }

    /**
     * Lista las evaluaciones activas de un curso (resumen, sin preguntas).
     *
     * @param courseId UUID del curso
     * @return respuesta 200 con la lista de {@link AssessmentSummary} del curso
     */
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

    /**
     * Obtiene los resultados de todos los estudiantes en una evaluación.
     *
     * <p>Solo el instructor propietario puede consultar los resultados.</p>
     *
     * @param assessmentId UUID de la evaluación
     * @return respuesta 200 con el mapa de resultados, 403 si no es el propietario,
     *         o 404 si la evaluación no existe
     */
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
                    .entity("{\"message\":\"No eres el instructor de esta evaluación\"}")
                    .build();
        }

        List<Map<String, Object>> results = buildSubmissionResults(assessmentId);
        return Response.ok(Map.of(
                "assessmentId",    assessmentId,
                "assessmentTitle", assessment.getTitle(),
                "submissions",     results
        )).build();
    }

    /**
     * Crea una evaluación con sus preguntas y opciones de respuesta.
     *
     * @param req datos de la evaluación a crear, incluyendo preguntas y opciones
     * @return respuesta 201 con el {@link AssessmentDetail} de la evaluación creada
     */
    @POST
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Crear evaluación con preguntas (INSTRUCTOR)")
    public Response create(@Valid AssessmentCreateRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        Assessment assessment = buildAssessmentFromRequest(req, instructorId);

        if (req.questions() != null) {
            assessment.getQuestions().addAll(
                    buildQuestions(req.questions(), assessment));
        }
        assessmentRepo.save(assessment);
        return Response.status(Response.Status.CREATED)
                .entity(AssessmentDetail.from(assessment)).build();
    }

    /**
     * Actualiza los datos y preguntas de una evaluación existente.
     *
     * <p>Solo el instructor propietario puede actualizar la evaluación.
     * Si se proporciona la lista de preguntas, reemplaza completamente
     * las preguntas existentes.</p>
     *
     * @param assessmentId UUID de la evaluación a actualizar
     * @param req          datos parciales a aplicar
     * @return respuesta 200 con el {@link AssessmentDetail} actualizado,
     *         403 si no es el propietario, o 404 si la evaluación no existe
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar evaluación (INSTRUCTOR)")
    public Response update(
            @PathParam("id") UUID assessmentId,
            AssessmentUpdateRequest req) {

        return assessmentRepo.findById(assessmentId)
                .map(a -> {
                    UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
                    if (!a.getInstructorId().equals(instructorId)) {
                        return Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"message\":\"No eres el instructor "
                                        + "de esta evaluación\"}")
                                .build();
                    }
                    applyUpdateFields(a, req);
                    if (req.questions() != null) {
                        a.getQuestions().clear();
                        a.getQuestions().addAll(buildQuestions(req.questions(), a));
                    }
                    assessmentRepo.save(a);
                    return Response.ok(AssessmentDetail.from(a)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada\"}").build());
    }

    /**
     * Elimina una evaluación de forma lógica (soft delete).
     *
     * <p>Solo el instructor propietario puede eliminar la evaluación.</p>
     *
     * @param assessmentId UUID de la evaluación a eliminar
     * @return respuesta 204 sin contenido, 403 si no es el propietario,
     *         o 404 si la evaluación no existe
     */
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
                                .entity("{\"message\":\"No eres el instructor "
                                        + "de esta evaluación\"}")
                                .build();
                    }
                    a.softDelete();
                    assessmentRepo.save(a);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada\"}").build());
    }

    /**
     * Envía y califica las respuestas de un estudiante para una evaluación.
     *
     * @param assessmentId UUID de la evaluación
     * @param req          respuestas y duración del intento
     * @return respuesta 200 con el {@link com.puj.assessments.dto.SubmissionResult}
     */
    @POST
    @Path("/{id}/submit")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Enviar respuestas de una evaluación")
    public Response submit(
            @PathParam("id") UUID assessmentId,
            @Valid com.puj.assessments.dto.SubmitRequest req) {

        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        com.puj.assessments.dto.SubmissionResult result =
                submissionService.submit(userId, assessmentId, req);
        return Response.ok(result).build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Construye la entidad {@link Assessment} a partir del request de creación.
     *
     * @param req          datos del request
     * @param instructorId UUID del instructor autenticado
     * @return instancia de {@link Assessment} lista para persistir
     */
    private Assessment buildAssessmentFromRequest(
            AssessmentCreateRequest req,
            UUID instructorId) {

        Assessment a = new Assessment();
        a.setTitle(req.title());
        a.setCourseId(req.courseId());
        a.setLessonId(req.lessonId());
        a.setDescription(req.description());
        a.setInstructorId(instructorId);
        if (req.passingScorePct() != null) a.setPassingScorePct(req.passingScorePct());
        if (req.maxAttempts() != null)     a.setMaxAttempts(req.maxAttempts());
        return a;
    }

    /**
     * Construye la lista de entidades {@link Question} con sus opciones a partir
     * de los DTOs del request.
     *
     * @param questionRequests lista de DTOs de preguntas
     * @param assessment       evaluación propietaria de las preguntas
     * @return lista de {@link Question} con sus {@link AnswerOption}s configuradas
     */
    private List<Question> buildQuestions(
            List<AssessmentCreateRequest.QuestionRequest> questionRequests,
            Assessment assessment) {

        List<Question> questions = new ArrayList<>();
        int qIdx = 0;
        for (AssessmentCreateRequest.QuestionRequest qr : questionRequests) {
            Question q = new Question();
            q.setAssessment(assessment);
            q.setText(qr.text());
            q.setType(QuestionType.valueOf(qr.type()));
            q.setPoints(qr.points() > 0 ? qr.points() : 1.0);
            q.setOrderIndex(++qIdx);
            if (qr.options() != null) {
                q.getOptions().addAll(buildOptions(qr.options(), q));
            }
            questions.add(q);
        }
        return questions;
    }

    /**
     * Construye la lista de entidades {@link AnswerOption} a partir de los DTOs.
     *
     * @param optionRequests lista de DTOs de opciones
     * @param question       pregunta propietaria de las opciones
     * @return lista de {@link AnswerOption} configuradas
     */
    private List<AnswerOption> buildOptions(
            List<AssessmentCreateRequest.OptionRequest> optionRequests,
            Question question) {

        List<AnswerOption> options = new ArrayList<>();
        int oIdx = 0;
        for (AssessmentCreateRequest.OptionRequest or : optionRequests) {
            AnswerOption opt = new AnswerOption();
            opt.setQuestion(question);
            opt.setText(or.text());
            opt.setCorrect(or.correct());
            opt.setOrderIndex(++oIdx);
            options.add(opt);
        }
        return options;
    }

    /**
     * Aplica los campos opcionales del request de actualización sobre la entidad.
     *
     * @param a   evaluación a modificar
     * @param req datos de actualización
     */
    private void applyUpdateFields(Assessment a, AssessmentUpdateRequest req) {
        if (req.title() != null && !req.title().isBlank()) a.setTitle(req.title());
        if (req.description() != null)    a.setDescription(req.description());
        a.setLessonId(req.lessonId());
        if (req.passingScorePct() != null) a.setPassingScorePct(req.passingScorePct());
        if (req.maxAttempts() != null)     a.setMaxAttempts(req.maxAttempts());
    }

    /**
     * Construye la lista de mapas de resultados de submissions para la respuesta
     * del endpoint de resultados del instructor.
     *
     * @param assessmentId UUID de la evaluación
     * @return lista de mapas con los campos de cada submission calificada
     */
    private List<Map<String, Object>> buildSubmissionResults(UUID assessmentId) {
        return submissionRepo.findByAssessment(assessmentId)
                .stream().map(s -> {
                    double pct = s.getMaxScore() != null
                            && s.getMaxScore().doubleValue() > 0
                            ? (s.getScore().doubleValue()
                               / s.getMaxScore().doubleValue()) * 100.0
                            : 0.0;
                    return Map.<String, Object>of(
                            "submissionId", s.getId(),
                            "userId",       s.getUserId(),
                            "score",        s.getScore() != null
                                    ? s.getScore() : BigDecimal.ZERO,
                            "maxScore",     s.getMaxScore() != null
                                    ? s.getMaxScore() : BigDecimal.ZERO,
                            "scorePct",     Math.round(pct * 10.0) / 10.0,
                            "passed",       s.isPassed(),
                            "attempt",      s.getAttemptNumber(),
                            "submittedAt",  s.getSubmittedAt() != null
                                    ? s.getSubmittedAt().toString() : ""
                    );
                }).toList();
    }
}
