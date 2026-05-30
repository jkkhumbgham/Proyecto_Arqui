package com.puj.users.rest;

import com.puj.security.jwt.JwtClaims;
import com.puj.security.jwt.JwtProvider;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.users.dto.LoginRequest;
import com.puj.users.dto.LoginResponse;
import com.puj.users.dto.RegisterRequest;
import com.puj.users.dto.UserResponse;
import com.puj.users.service.AuthService;
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
public class AuthResource {

    @Inject private AuthService       authService;
    @Inject private AuthenticatedUser authenticatedUser;
    @Inject private JwtProvider       jwtProvider;

    /**
     * Registra un nuevo usuario en la plataforma con rol {@code STUDENT}.
     *
     * @param req datos del formulario de registro con consentimiento de datos.
     * @return respuesta HTTP 201 con los datos públicos del usuario creado.
     */
    @POST
    @Path("/register")
    @Operation(summary = "Registro de nuevo usuario")
    public Response register(@Valid RegisterRequest req) {
        UserResponse user = authService.register(req);
        return Response.status(Response.Status.CREATED).entity(user).build();
    }

    /**
     * Autentica al usuario con correo y contraseña y emite un par de tokens.
     *
     * <p>La dirección IP se extrae del encabezado {@code X-Forwarded-For} para
     * registro de auditoría.
     *
     * @param req     credenciales de inicio de sesión.
     * @param headers encabezados HTTP de la solicitud.
     * @return respuesta con access token, refresh token y datos del usuario.
     */
    @POST
    @Path("/login")
    @Operation(summary = "Inicio de sesión")
    public LoginResponse login(@Valid LoginRequest req,
                               @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        return authService.login(req, ip);
    }

    /**
     * Renueva el access token usando un refresh token válido.
     *
     * @param refreshToken token de renovación en formato {@code "<uuid>:<raw>"}.
     * @return nueva respuesta con el access token renovado.
     */
    @POST
    @Path("/refresh")
    @Operation(summary = "Renovar access token")
    public LoginResponse refresh(
            @QueryParam("token") @NotBlank String refreshToken) {
        return authService.refreshAccessToken(refreshToken);
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
    public Response logout(@Context HttpHeaders headers) {
        if (!authenticatedUser.isAuthenticated()) {
            return Response.noContent().build();
        }
        JwtClaims claims = jwtProvider.validateToken(authenticatedUser.getRawToken());
        String ip = headers.getHeaderString("X-Forwarded-For");
        authService.logout(claims, ip);
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
    public Response me() {
        if (!authenticatedUser.isAuthenticated()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(buildError("UNAUTHORIZED", "No autenticado"))
                    .build();
        }
        return Response.ok(new UserResponse(
                UUID.fromString(authenticatedUser.getUserId()),
                authenticatedUser.getEmail(),
                null,
                null,
                authenticatedUser.getRole(),
                true,
                null,
                null
        )).build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Construye un cuerpo de error JSON mínimo para respuestas de error.
     *
     * @param code    código de error legible por máquina.
     * @param message mensaje descriptivo del error.
     * @return cadena JSON con los campos {@code error}, {@code message} y
     *         {@code timestamp}.
     */
    private String buildError(String code, String message) {
        return String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                code, message, Instant.now());
    }
}
