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

/**
 * Filtro JAX-RS que valida el JWT Bearer de cada solicitud entrante.
 *
 * <p>Se ejecuta antes de los interceptores RBAC. Si el header
 * {@code Authorization: Bearer <token>} está ausente, el filtro no actúa
 * (la solicitud puede ser pública). Si está presente pero inválido, aborta
 * con HTTP 401.</p>
 *
 * <p>Flujo de validación:</p>
 * <ol>
 *   <li>Verifica firma RS256 y expiración ({@link JwtProvider#validateToken(String)})</li>
 *   <li>Verifica que el {@code jti} no esté en la blacklist Redis</li>
 *   <li>Verifica que el token no haya expirado (doble check)</li>
 *   <li>Puebla {@link AuthenticatedUser} en el scope de la solicitud</li>
 * </ol>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see JwtProvider
 * @see TokenBlacklistService
 * @see AuthenticatedUser
 */
@Provider
public class JwtFilter implements ContainerRequestFilter {

    @Inject private JwtProvider           jwtProvider;
    @Inject private TokenBlacklistService blacklistService;
    @Inject private AuthenticatedUser     authenticatedUser;

    /**
     * Intercepta cada solicitud HTTP para validar el JWT si está presente.
     *
     * @param ctx contexto de la solicitud JAX-RS; se aborta con 401 si el token es inválido
     */
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

    /**
     * Aborta la solicitud con HTTP 401 y un cuerpo JSON estructurado.
     *
     * @param ctx     contexto de la solicitud a abortar
     * @param code    código de error legible por máquina
     * @param message mensaje legible por humanos
     */
    private void abort(ContainerRequestContext ctx, String code, String message) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(String.format(
                        "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                        code, message, Instant.now()))
                .build());
    }
}
