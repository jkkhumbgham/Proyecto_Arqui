package com.puj.usuarios.gestion.interfaces;

import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaUsuario;
import com.puj.usuarios.gestion.aplicacion.ServicioGestionUsuarios;
import com.puj.usuarios.gestion.interfaces.dto.SolicitudActualizarUsuario;
import com.puj.usuarios.gestion.interfaces.dto.SolicitudCrearUsuario;
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
public class RecursoUsuarios {

    @Inject private ServicioGestionUsuarios servicioGestionUsuarios;
    @Inject private UsuarioAutenticado      usuarioAutenticado;

    /**
     * Devuelve una lista paginada de usuarios activos o inactivos.
     *
     * <p>Cuando {@code inactive=true} devuelve únicamente usuarios que no han
     * iniciado sesión en los últimos {@code days} días.
     *
     * @param pagina   número de página (por defecto 0).
     * @param cantidad número de resultados por página (por defecto 20, máximo 100).
     * @param inactive si es {@code true}, filtra únicamente usuarios inactivos.
     * @param dias     umbral de inactividad en días (por defecto 30).
     * @return respuesta HTTP 200 con la lista de usuarios y metadatos de paginación.
     */
    @GET
    @RequiereRol({Rol.ADMIN, Rol.DIRECTOR})
    @Operation(summary = "Listar todos los usuarios (ADMIN/DIRECTOR); "
            + "?inactive=true&days=30 para inactivos")
    public Response listar(
            @QueryParam("page")     @DefaultValue("0")  int     pagina,
            @QueryParam("size")     @DefaultValue("20") int     cantidad,
            @QueryParam("inactive")                     boolean inactive,
            @QueryParam("days")     @DefaultValue("30") int     dias) {

        if (inactive) {
            List<RespuestaUsuario> usuarios =
                    servicioGestionUsuarios.buscarInactivos(dias, pagina, Math.min(cantidad, 100));
            return Response.ok(Map.of("data", usuarios, "page", pagina)).build();
        }

        List<RespuestaUsuario> usuarios = servicioGestionUsuarios.buscarTodos(pagina, Math.min(cantidad, 100));
        long total = servicioGestionUsuarios.contar();
        return Response.ok(
                Map.of("data", usuarios, "total", total, "page", pagina)).build();
    }

    /**
     * Crea un nuevo usuario desde el panel de administración con rol configurable.
     *
     * @param solicitud datos del nuevo usuario incluyendo rol opcional.
     * @param headers   encabezados HTTP para extraer la IP del cliente.
     * @return respuesta HTTP 201 con los datos del usuario creado.
     */
    @POST
    @RequiereRol(Rol.ADMIN)
    @Operation(summary = "Crear usuario desde admin (ADMIN)")
    public Response crearDesdeAdmin(@Valid SolicitudCrearUsuario solicitud,
                                    @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID idAdmin = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        RespuestaUsuario creado = servicioGestionUsuarios.crearDesdeAdmin(solicitud, idAdmin, ip);
        return Response.status(Response.Status.CREATED).entity(creado).build();
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
    public RespuestaUsuario buscarPorId(@PathParam("id") UUID id) {
        verificarSelfOAdmin(id);
        return servicioGestionUsuarios.buscarPorId(id);
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
    public Response obtenerNombreMostrar(@PathParam("id") UUID id) {
        if (!usuarioAutenticado.estaAutenticado()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }
        RespuestaUsuario u = servicioGestionUsuarios.buscarPorId(id);
        String nombreMostrar = (u.nombre() + " " + u.apellido()).trim();
        if (nombreMostrar.isBlank()) {
            nombreMostrar = u.email();
        }
        return Response.ok(
                Map.of("id", id.toString(), "displayName", nombreMostrar)).build();
    }

    /**
     * Actualiza los datos personales y/o contraseña de un usuario.
     *
     * <p>Solo el propio usuario o un administrador pueden actualizar el perfil.
     *
     * @param id       UUID del usuario a actualizar.
     * @param solicitud datos a actualizar; los campos nulos se ignoran.
     * @param headers  encabezados HTTP para extraer la IP del cliente.
     * @return datos públicos del usuario actualizado.
     * @throws NotAuthorizedException si el solicitante no está autenticado.
     * @throws ForbiddenException     si el solicitante no tiene permisos.
     */
    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualizar datos de usuario")
    public RespuestaUsuario actualizar(@PathParam("id") UUID id,
                                       @Valid SolicitudActualizarUsuario solicitud,
                                       @Context HttpHeaders headers) {
        verificarSelfOAdmin(id);
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID realizadoPor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        return servicioGestionUsuarios.actualizar(id, solicitud, realizadoPor, ip);
    }

    /**
     * Cambia el rol de seguridad de un usuario existente.
     *
     * @param id        UUID del usuario cuyo rol se cambia.
     * @param nombreRol nombre del nuevo rol en mayúsculas (ej. {@code "INSTRUCTOR"}).
     * @param headers   encabezados HTTP para extraer la IP del cliente.
     * @return respuesta HTTP 204 sin cuerpo si la operación fue exitosa.
     */
    @PUT
    @Path("/{id}/role")
    @RequiereRol(Rol.ADMIN)
    @Operation(summary = "Cambiar rol de usuario (ADMIN)")
    public Response cambiarRol(@PathParam("id") UUID id,
                                @QueryParam("role") String nombreRol,
                                @Context HttpHeaders headers) {
        Rol nuevoRol = Rol.valueOf(nombreRol.toUpperCase());
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID idAdmin = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        servicioGestionUsuarios.cambiarRol(id, nuevoRol, idAdmin, ip);
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
    @RequiereRol(Rol.ADMIN)
    @Operation(summary = "Eliminar usuario (soft delete, ADMIN)")
    public Response eliminar(@PathParam("id") UUID id,
                              @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        UUID idAdmin = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        servicioGestionUsuarios.eliminar(id, idAdmin, ip);
        return Response.noContent().build();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Verifica que el solicitante sea el propio usuario objetivo o un administrador.
     *
     * @param idObjetivo UUID del usuario objetivo de la operación.
     * @throws NotAuthorizedException si el solicitante no está autenticado.
     * @throws ForbiddenException     si el solicitante no es self ni ADMIN.
     */
    private void verificarSelfOAdmin(UUID idObjetivo) {
        if (!usuarioAutenticado.estaAutenticado()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }
        boolean esSelf  = usuarioAutenticado.obtenerIdUsuario().equals(idObjetivo.toString());
        boolean esAdmin = usuarioAutenticado.obtenerRol() == Rol.ADMIN;
        if (!esSelf && !esAdmin) {
            throw new ForbiddenException("Acceso denegado.");
        }
    }
}
