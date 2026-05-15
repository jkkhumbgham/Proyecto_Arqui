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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticación")
public class AuthResource {

    @Inject private AuthService       authService;
    @Inject private AuthenticatedUser authenticatedUser;
    @Inject private JwtProvider       jwtProvider;

    @POST
    @Path("/register")
    @Operation(summary = "Registro de nuevo usuario")
    public Response register(@Valid RegisterRequest req) {
        UserResponse user = authService.register(req);
        return Response.status(Response.Status.CREATED).entity(user).build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Inicio de sesión")
    public LoginResponse login(@Valid LoginRequest req,
                               @Context HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        return authService.login(req, ip);
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Renovar access token")
    public LoginResponse refresh(@QueryParam("token") @NotBlank String refreshToken) {
        return authService.refreshAccessToken(refreshToken);
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Cerrar sesión")
    public Response logout() {
        if (!authenticatedUser.isAuthenticated()) {
            return Response.noContent().build();
        }
        JwtClaims claims = jwtProvider.validateToken(authenticatedUser.getRawToken());
        authService.logout(claims);
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    @Operation(summary = "Perfil del usuario autenticado")
    public Response me() {
        if (!authenticatedUser.isAuthenticated()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(error("UNAUTHORIZED", "No autenticado"))
                    .build();
        }
        return Response.ok(new UserResponse(
                java.util.UUID.fromString(authenticatedUser.getUserId()),
                authenticatedUser.getEmail(), null, null,
                authenticatedUser.getRole(), true, null
        )).build();
    }

    private String error(String code, String message) {
        return String.format("{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                code, message, Instant.now());
    }
}
