package com.puj.users.rest;

import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import com.puj.users.dto.AdminCreateUserRequest;
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

/**
 * Recurso JAX-RS que expone los endpoints de gestión de usuarios.
 *
 * <p>Rutas disponibles bajo {@code /api/v1/users}:
 * <ul>
 *   <li>{@code GET    /}          — lista paginada de usuarios (ADMIN/DIRECTOR).</li>
 *   <li>{@code POST   /}          — crear usuario desde administración (ADMIN).</li>
 *   <li>{@code GET    /{id}}       — obtener usuario por ID (self o ADMIN).</li>
 *   <li>{@code GET    /{id}/name}  — nombre para mostrar (cualquier usuario autenticado).</li>
 *   <li>{@code PUT    /{id}}       — actualizar datos (self o ADMIN).</li>
 *   <li>{@code PUT    /{id}/role}  — cambiar rol (ADMIN).</li>
 *   <li>{@code DELETE /{id}}       — borrado lógico (ADMIN).</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Usuarios")
public class UserResource {

    @Inject private UserService       userService;
    @Inject private AuthenticatedUser authenticatedUser;

    /**
     * Devuelve una lista paginada de usuarios activos o inactivos.
     *
     * <p>Cuando {@code inactive=true} devuelve únicamente usuarios que no han
     * iniciado sesión en los últimos {@code days} días.
     *
     * @param page     número de página (por defecto 0).
     * @param size     número de resultados por página (por defecto 20, máximo 100).
     * @param inactive si es {@code true}, filtra únicamente usuarios inactivos.
     * @param days     umbral de inactividad en días (por defecto 30).
     * @return respuesta HTTP 200 con la lista de usuarios y metadatos de paginación.
     */
    @GET
    @RequiresRole({Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Listar todos los usuarios (ADMIN/DIRECTOR); "
            + "?inactive=true&days=30 para inactivos")
    public Response findAll(
            @QueryParam("page")     @DefaultValue("0")  int     page,
            @QueryParam("size")     @DefaultValue("20") int     size,
            @QueryParam("inactive")                     boolean inactive,
            @QueryParam("days")     @DefaultValue("30") int     days) {

        if (inactive) {
            List<UserResponse> users =
                    userService.findInactive(days, page, Math.min(size, 100));
            return Response.ok(Map.of("data", users, "page", page)).build();
        }

        List<UserResponse> users = userService.findAll(page, Math.min(size, 100));
        long total = userService.count();
        return Response.ok(
                Map.of("data", users, "total", total, "page", page)).build();
    }

    /**
     * Crea un nuevo usuario desde el panel de administración con rol configurable.
     *
     * @param req     datos del nuevo usuario incluyendo rol opcional.
     * @param headers encabezados HTTP para extraer la IP del cliente.
     * @return respuesta HTTP 201 con los datos del usuario creado.
     */
    @POST
    @RequiresRole(Role.ADMIN)
    @Operation(summary = "Crear usuario desde admin (ADMIN)")
    public Response adminCreate(@Valid AdminCreateUserRequest req,
                                @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID adminId = UUID.fromString(authenticatedUser.getUserId());
        UserResponse created = userService.adminCreate(req, adminId, ip);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Devuelve los datos públicos de un usuario identificado por su UUID.
     *
     * <p>Solo el propio usuario o un administrador pueden consultar este recurso.
     *
     * @param id UUID del usuario a consultar.
     * @return datos públicos del usuario.
     * @throws NotAuthorizedException si el solicitante no está autenticado.
     * @throws ForbiddenException     si el solicitante no es el propio usuario
     *                                ni un administrador.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener usuario por ID")
    public UserResponse findById(@PathParam("id") UUID id) {
        requireSelfOrAdmin(id);
        return userService.findById(id);
    }

    /**
     * Devuelve el nombre para mostrar de un usuario dado su UUID.
     *
     * <p>Cualquier usuario autenticado puede consultar el nombre de otro usuario
     * para mostrar en la interfaz (ej. nombre de instructor en un curso).
     *
     * @param id UUID del usuario cuyo nombre se desea obtener.
     * @return respuesta HTTP 200 con los campos {@code id} y {@code displayName}.
     * @throws NotAuthorizedException si el solicitante no está autenticado.
     */
    @GET
    @Path("/{id}/name")
    @Operation(summary = "Obtener nombre para mostrar de un usuario "
            + "(cualquier usuario autenticado)")
    public Response getDisplayName(@PathParam("id") UUID id) {
        if (!authenticatedUser.isAuthenticated()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }
        UserResponse u = userService.findById(id);
        String displayName = (u.firstName() + " " + u.lastName()).trim();
        if (displayName.isBlank()) {
            displayName = u.email();
        }
        return Response.ok(
                Map.of("id", id.toString(), "displayName", displayName)).build();
    }

    /**
     * Actualiza los datos personales y/o contraseña de un usuario.
     *
     * <p>Solo el propio usuario o un administrador pueden actualizar el perfil.
     *
     * @param id      UUID del usuario a actualizar.
     * @param req     datos a actualizar; los campos nulos se ignoran.
     * @param headers encabezados HTTP para extraer la IP del cliente.
     * @return datos públicos del usuario actualizado.
     * @throws NotAuthorizedException si el solicitante no está autenticado.
     * @throws ForbiddenException     si el solicitante no tiene permisos.
     */
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

    /**
     * Cambia el rol de seguridad de un usuario existente.
     *
     * @param id       UUID del usuario cuyo rol se cambia.
     * @param roleName nombre del nuevo rol en mayúsculas (ej. {@code "INSTRUCTOR"}).
     * @param headers  encabezados HTTP para extraer la IP del cliente.
     * @return respuesta HTTP 204 sin cuerpo si la operación fue exitosa.
     */
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

    /**
     * Realiza el borrado lógico de un usuario desactivando su cuenta.
     *
     * @param id      UUID del usuario a eliminar.
     * @param headers encabezados HTTP para extraer la IP del cliente.
     * @return respuesta HTTP 204 sin cuerpo si la operación fue exitosa.
     */
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

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Verifica que el solicitante sea el propio usuario objetivo o un administrador.
     *
     * @param targetId UUID del usuario objetivo de la operación.
     * @throws NotAuthorizedException si el solicitante no está autenticado.
     * @throws ForbiddenException     si el solicitante no es self ni ADMIN.
     */
    private void requireSelfOrAdmin(UUID targetId) {
        if (!authenticatedUser.isAuthenticated()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }
        boolean isSelf  = authenticatedUser.getUserId().equals(targetId.toString());
        boolean isAdmin = authenticatedUser.getRole() == Role.ADMIN;
        if (!isSelf && !isAdmin) {
            throw new ForbiddenException("Acceso denegado.");
        }
    }
}
