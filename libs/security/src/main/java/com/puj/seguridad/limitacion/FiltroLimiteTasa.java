package com.puj.seguridad.limitacion;

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
 * Límite: 100 req/min por IP de origen (RNF-03d).
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class FiltroLimiteTasa implements ContainerRequestFilter {

    private static final Set<String> SUFIJOS_LIMITADOS = Set.of(
            "/auth/login", "/auth/register", "/auth/refresh"
    );

    private static final String CUERPO_429 =
            "{\"error\":\"TOO_MANY_REQUESTS\","
            + "\"message\":\"Límite de solicitudes excedido. Intenta en 60 segundos.\"}";

    @Inject
    private ServicioLimiteTasa limiteTasa;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String ruta = ctx.getUriInfo().getPath();
        if (SUFIJOS_LIMITADOS.stream().noneMatch(ruta::endsWith)) return;

        String ip = extraerIp(ctx);
        if (!limiteTasa.estaPermitido(ip)) {
            ctx.abortWith(Response.status(429)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(CUERPO_429)
                    .build());
        }
    }

    private String extraerIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return "unknown";
    }
}
