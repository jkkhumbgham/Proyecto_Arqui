package com.puj.seguridad.correlacion;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/**
 * Propaga el X-Correlation-ID entre solicitud y respuesta.
 * Genera un nuevo UUID cuando está ausente (solicitudes originadas en el cliente).
 * Requerido por RNF-06c: trazabilidad distribuida entre servicios.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR - 100)
public class FiltroIdCorrelacion implements ContainerRequestFilter, ContainerResponseFilter {

    static final String HEADER = "X-Correlation-ID";

    @Inject
    private ContextoCorrelacion ctx;

    @Override
    public void filter(ContainerRequestContext req) {
        String id = req.getHeaderString(HEADER);
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        ctx.establecerIdCorrelacion(id);
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext resp) {
        if (ctx.obtenerIdCorrelacion() != null) {
            resp.getHeaders().putSingle(HEADER, ctx.obtenerIdCorrelacion());
        }
    }
}
