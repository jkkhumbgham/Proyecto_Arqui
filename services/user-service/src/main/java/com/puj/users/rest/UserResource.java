package com.puj.users.rest;

import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import com.puj.users.dto.UpdateUserRequest;
import com.puj.users.dto.UserResponse;
import com.puj.users.service.UserService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Usuarios")
public class UserResource {

    @Inject private UserService       userService;
    @Inject private AuthenticatedUser authenticatedUser;

    @GET
    @RequiresRole(Role.ADMIN)
    @Operation(summary = "Listar todos los usuarios (ADMIN)")
    public Response findAll(@QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("20") int size) {
        List<UserResponse> users = userService.findAll(page, Math.min(size, 100));
        long total = userService.count();
        return Response.ok(Map.of("data", users, "total", total, "page", page)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener usuario por ID")
    public UserResponse findById(@PathParam("id") UUID id) {
        requireSelfOrAdmin(id);
        return userService.findById(id);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualizar datos de usuario")
    public UserResponse update(@PathParam("id") UUID id,
                               @Valid UpdateUserRequest req,
                               @Context HttpHeaders headers) {
        requireSelfOrAdmin(id);
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID performedBy = UUID.fromString(authenticatedUser.getUserId());
        return userService.update(id, req, performedBy, ip);
    }

    @PUT
    @Path("/{id}/role")
    @RequiresRole(Role.ADMIN)
    @Operation(summary = "Cambiar rol de usuario (ADMIN)")
    public Response changeRole(@PathParam("id") UUID id,
                               @QueryParam("role") String roleName,
                               @Context HttpHeaders headers) {
        Role newRole = Role.valueOf(roleName.toUpperCase());
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID adminId = UUID.fromString(authenticatedUser.getUserId());
        userService.changeRole(id, newRole, adminId, ip);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole(Role.ADMIN)
    @Operation(summary = "Eliminar usuario (soft delete, ADMIN)")
    public Response delete(@PathParam("id") UUID id,
                           @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID adminId = UUID.fromString(authenticatedUser.getUserId());
        userService.softDelete(id, adminId, ip);
        return Response.noContent().build();
    }

    private void requireSelfOrAdmin(UUID targetId) {
        if (!authenticatedUser.isAuthenticated()) throw new NotAuthorizedException("Bearer realm=\"puj\"");
        boolean isSelf  = authenticatedUser.getUserId().equals(targetId.toString());
        boolean isAdmin = authenticatedUser.getRole() == Role.ADMIN;
        if (!isSelf && !isAdmin) throw new ForbiddenException("Acceso denegado.");
    }
}
