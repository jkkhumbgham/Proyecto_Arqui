package com.puj.usuarios.autenticacion.interfaces;

import com.puj.seguridad.jwt.ReclamosJwt;
import com.puj.seguridad.jwt.ProveedorJwt;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import com.puj.usuarios.autenticacion.aplicacion.ServicioAutenticacion;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudRegistro;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaUsuario;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.UUID;

/**
 * Recurso JAX-RS que expone los endpoints de autenticación de la plataforma.
 *
 * <p>Rutas disponibles bajo {@code /api/v1/auth}:
 * <ul>
 *   <li>{@code POST /register} — registro de nuevo usuario.</li>
 *   <li>{@code POST /login}    — inicio de sesión y emisión de tokens.</li>
 *   <li>{@code POST /refresh}  — renovación del access token.</li>
 *   <li>{@code POST /logout}   — cierre de sesión e invalidación de tokens.</li>
 *   <li>{@code GET  /me}       — datos del usuario autenticado.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticación")
public class RecursoAutenticacion {

    @Inject private ServicioAutenticacion servicioAutenticacion;
    @Inject private UsuarioAutenticado    usuarioAutenticado;
    @Inject private ProveedorJwt          proveedorJwt;

    /**
     * Registra un nuevo usuario en la plataforma con rol {@code STUDENT}.
     *
     * @param solicitud datos del formulario de registro con consentimiento de datos.
     * @return respuesta HTTP 201 con los datos públicos del usuario creado.
     */
    @POST
    @Path("/register")
    @Operation(summary = "Registro de nuevo usuario")
    public Response registrar(@Valid SolicitudRegistro solicitud) {
        RespuestaUsuario usuario = servicioAutenticacion.registrar(solicitud);
        return Response.status(Response.Status.CREATED).entity(usuario).build();
    }

    /**
     * Autentica al usuario con correo y contraseña y emite un par de tokens.
     *
     * <p>La dirección IP se extrae del encabezado {@code X-Forwarded-For} para
     * registro de auditoría.
     *
     * @param solicitud credenciales de inicio de sesión.
     * @param headers   encabezados HTTP de la solicitud.
     * @return respuesta con access token, refresh token y datos del usuario.
     */
    @POST
    @Path("/login")
    @Operation(summary = "Inicio de sesión")
    public RespuestaLogin iniciarSesion(@Valid SolicitudLogin solicitud,
                                        @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        return servicioAutenticacion.iniciarSesion(solicitud, ip);
    }

    /**
     * Renueva el access token usando un refresh token válido.
     *
     * @param tokenRefresh token de renovación en formato {@code "<uuid>:<raw>"}.
     * @return nueva respuesta con el access token renovado.
     */
    @POST
    @Path("/refresh")
    @Operation(summary = "Renovar access token")
    public RespuestaLogin renovar(
            @QueryParam("token") @NotBlank String tokenRefresh) {
        return servicioAutenticacion.renovarToken(tokenRefresh);
    }

    /**
     * Cierra la sesión del usuario autenticado invalidando sus tokens activos.
     *
     * <p>Si la solicitud no lleva token válido el método devuelve 204 sin error.
     *
     * @param headers encabezados HTTP de la solicitud.
     * @return respuesta HTTP 204 sin cuerpo.
     */
    @POST
    @Path("/logout")
    @Operation(summary = "Cerrar sesión")
    public Response cerrarSesion(@Context HttpHeaders headers) {
        if (!usuarioAutenticado.estaAutenticado()) {
            return Response.noContent().build();
        }
        ReclamosJwt reclamos = proveedorJwt.validarToken(usuarioAutenticado.obtenerTokenBruto());
        String ip = headers.getHeaderString("X-Forwarded-For");
        servicioAutenticacion.cerrarSesion(reclamos, ip);
        return Response.noContent().build();
    }

    /**
     * Devuelve el perfil básico del usuario autenticado extraído del JWT.
     *
     * @return respuesta HTTP 200 con los datos del usuario o 401 si no está
     *         autenticado.
     */
    @GET
    @Path("/me")
    @Operation(summary = "Perfil del usuario autenticado")
    public Response perfil() {
        if (!usuarioAutenticado.estaAutenticado()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(construirError("UNAUTHORIZED", "No autenticado"))
                    .build();
        }
        return Response.ok(new RespuestaUsuario(
                UUID.fromString(usuarioAutenticado.obtenerIdUsuario()),
                usuarioAutenticado.obtenerCorreo(),
                null,
                null,
                usuarioAutenticado.obtenerRol(),
                true,
                null,
                null
        )).build();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Construye un cuerpo de error JSON mínimo para respuestas de error.
     *
     * @param codigo  código de error legible por máquina.
     * @param mensaje mensaje descriptivo del error.
     * @return cadena JSON con los campos {@code error}, {@code message} y
     *         {@code timestamp}.
     */
    private String construirError(String codigo, String mensaje) {
        return String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                codigo, mensaje, Instant.now());
    }
}
