package com.puj.users.rest;

import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import com.puj.users.service.GamificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Gamificación")
public class GamificationResource {

    @Inject private GamificationService gamificationService;
    @Inject private AuthenticatedUser   authenticatedUser;

    @GET
    @Path("/{id}/points")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Puntos de un usuario")
    public Response getPoints(@PathParam("id") UUID userId) {
        return Response.ok(gamificationService.getPoints(userId)).build();
    }

    @GET
    @Path("/{id}/badges")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Insignias de un usuario")
    public Response getBadges(@PathParam("id") UUID userId) {
        return Response.ok(gamificationService.getBadges(userId)).build();
    }

    @GET
    @Path("/me/points")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Mis puntos")
    public Response myPoints() {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        return Response.ok(gamificationService.getPoints(userId)).build();
    }

    @GET
    @Path("/me/badges")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Mis insignias")
    public Response myBadges() {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        return Response.ok(gamificationService.getBadges(userId)).build();
    }
}
