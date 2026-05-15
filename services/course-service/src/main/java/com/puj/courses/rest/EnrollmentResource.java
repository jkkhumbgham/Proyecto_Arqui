package com.puj.courses.rest;

import com.puj.courses.dto.EnrollmentResponse;
import com.puj.courses.service.EnrollmentService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/enrollments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Inscripciones")
public class EnrollmentResource {

    @Inject private EnrollmentService enrollmentService;
    @Inject private AuthenticatedUser  authenticatedUser;

    @POST
    @Path("/courses/{courseId}")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Inscribirse en un curso (STUDENT)")
    public Response enroll(@PathParam("courseId") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        EnrollmentResponse enrollment = enrollmentService.enroll(userId, courseId);
        return Response.status(Response.Status.CREATED).entity(enrollment).build();
    }

    @GET
    @Path("/my")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Mis inscripciones")
    public List<EnrollmentResponse> myEnrollments() {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        return enrollmentService.findByUser(userId);
    }

    @DELETE
    @Path("/courses/{courseId}")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Cancelar inscripción")
    public Response cancel(@PathParam("courseId") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        enrollmentService.cancel(userId, courseId);
        return Response.noContent().build();
    }
}
