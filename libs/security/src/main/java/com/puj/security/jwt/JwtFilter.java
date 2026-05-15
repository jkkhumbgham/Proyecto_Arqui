package com.puj.security.jwt;

import com.puj.security.blacklist.TokenBlacklistService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;

@Provider
public class JwtFilter implements ContainerRequestFilter {

    @Inject
    private JwtProvider jwtProvider;

    @Inject
    private TokenBlacklistService blacklistService;

    @Inject
    private AuthenticatedUser authenticatedUser;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String authHeader = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7);
        try {
            JwtClaims claims = jwtProvider.validateToken(token);

            if (blacklistService.isBlacklisted(claims.jti())) {
                abort(ctx, "TOKEN_REVOKED", "El token ha sido revocado");
                return;
            }

            if (claims.expiresAt().isBefore(Instant.now())) {
                abort(ctx, "TOKEN_EXPIRED", "El token ha expirado");
                return;
            }

            authenticatedUser.populate(
                    claims.userId(),
                    claims.email(),
                    Role.valueOf(claims.role()),
                    token
            );

        } catch (JwtProvider.TokenValidationException e) {
            abort(ctx, e.getCode(), e.getMessage());
        } catch (Exception e) {
            abort(ctx, "INVALID_TOKEN", "Token inválido");
        }
    }

    private void abort(ContainerRequestContext ctx, String code, String message) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(String.format(
                        "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                        code, message, Instant.now()))
                .build());
    }
}
