package com.puj.security.ratelimit;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Set;

/**
 * Aplica rate limiting a los endpoints públicos de autenticación.
 * Limit: 100 req/min por IP de origen (RNF-03d).
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Set<String> RATE_LIMITED_SUFFIXES = Set.of(
            "/auth/login", "/auth/register", "/auth/refresh"
    );

    private static final String BODY_429 =
            "{\"error\":\"TOO_MANY_REQUESTS\","
            + "\"message\":\"Límite de solicitudes excedido. Intenta en 60 segundos.\"}";

    @Inject
    private RateLimiterService rateLimiter;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (RATE_LIMITED_SUFFIXES.stream().noneMatch(path::endsWith)) return;

        String ip = extractIp(ctx);
        if (!rateLimiter.isAllowed(ip)) {
            ctx.abortWith(Response.status(429)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(BODY_429)
                    .build());
        }
    }

    private String extractIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return "unknown";
    }
}
