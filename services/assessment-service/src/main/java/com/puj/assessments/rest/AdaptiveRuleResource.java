package com.puj.assessments.rest;

import com.puj.assessments.adaptive.AdaptiveEngine;
import com.puj.assessments.entity.AdaptiveRule;
import com.puj.assessments.entity.Submission;
import com.puj.assessments.entity.SubmissionStatus;
import com.puj.assessments.repository.AdaptiveRuleRepository;
import com.puj.assessments.repository.SubmissionRepository;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/adaptive-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reglas Adaptativas")
public class AdaptiveRuleResource {

    @Inject private AdaptiveRuleRepository ruleRepo;
    @Inject private SubmissionRepository   submissionRepo;
    @Inject private AdaptiveEngine         adaptiveEngine;
    @Inject private AuthenticatedUser      authenticatedUser;

    @POST
    @RequiresRole(Role.INSTRUCTOR)
    @Transactional
    @Operation(summary = "Crear/actualizar regla adaptativa de una evaluación (INSTRUCTOR)")
    public Response upsert(AdaptiveRule rule) {
        rule.setInstructorId(UUID.fromString(authenticatedUser.getUserId()));
        ruleRepo.save(rule);
        adaptiveEngine.invalidateCache(rule.getAssessmentId());
        return Response.status(Response.Status.CREATED).entity(rule).build();
    }

    @GET
    @Path("/assessments/{assessmentId}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Consultar regla adaptativa de una evaluación")
    public Response findByAssessment(@PathParam("assessmentId") UUID assessmentId) {
        return ruleRepo.findByAssessment(assessmentId)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Retorna los IDs de lecciones suplementarias desbloqueadas para el estudiante actual
     * en un curso dado (i.e., evaluaciones donde el estudiante no superó el umbral).
     */
    @GET
    @Path("/unlocked-supplementary")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Lecciones suplementarias desbloqueadas para el estudiante en un curso")
    public Response getUnlockedSupplementary(@QueryParam("courseId") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        List<AdaptiveRule> rules = ruleRepo.findByCourse(courseId);
        List<String> unlockedIds = new ArrayList<>();
        for (AdaptiveRule rule : rules) {
            if (rule.getSupplementaryLessonId() == null) continue;
            List<Submission> subs = submissionRepo.findByUserAndAssessment(userId, rule.getAssessmentId());
            boolean fired = subs.stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.GRADED
                              && s.getMaxScore() != null
                              && s.getMaxScore().compareTo(BigDecimal.ZERO) > 0)
                    .anyMatch(s -> s.getScore().doubleValue() / s.getMaxScore().doubleValue() * 100.0
                                   < rule.getScoreThresholdPct());
            if (fired) unlockedIds.add(rule.getSupplementaryLessonId().toString());
        }
        return Response.ok(unlockedIds).build();
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole(Role.INSTRUCTOR)
    @Transactional
    @Operation(summary = "Eliminar regla adaptativa (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        ruleRepo.findById(id).ifPresent(r -> {
            r.softDelete();
            ruleRepo.save(r);
            adaptiveEngine.invalidateCache(r.getAssessmentId());
        });
        return Response.noContent().build();
    }
}
