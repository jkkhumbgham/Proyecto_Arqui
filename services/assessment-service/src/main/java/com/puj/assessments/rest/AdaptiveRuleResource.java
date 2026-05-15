package com.puj.assessments.rest;

import com.puj.assessments.adaptive.AdaptiveEngine;
import com.puj.assessments.entity.AdaptiveRule;
import com.puj.assessments.repository.AdaptiveRuleRepository;
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

import java.util.UUID;

@Path("/adaptive-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reglas Adaptativas")
public class AdaptiveRuleResource {

    @Inject private AdaptiveRuleRepository ruleRepo;
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
