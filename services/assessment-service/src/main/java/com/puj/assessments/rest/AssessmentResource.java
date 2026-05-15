package com.puj.assessments.rest;

import com.puj.assessments.dto.SubmitRequest;
import com.puj.assessments.dto.SubmissionResult;
import com.puj.assessments.service.SubmissionService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/assessments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Evaluaciones")
public class AssessmentResource {

    @Inject private SubmissionService submissionService;
    @Inject private AuthenticatedUser authenticatedUser;

    @POST
    @Path("/{id}/submit")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Enviar respuestas de una evaluación")
    public Response submit(@PathParam("id") UUID assessmentId,
                           @Valid SubmitRequest req) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        SubmissionResult result = submissionService.submit(userId, assessmentId, req);
        return Response.ok(result).build();
    }
}
