package com.puj.security.correlation;

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
 * Propagates X-Correlation-ID across request/response.
 * Generates a new UUID when absent (client-originated requests).
 * Required by RNF-06c: trazabilidad distribuida entre servicios.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR - 100)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String HEADER = "X-Correlation-ID";

    @Inject
    private CorrelationContext ctx;

    @Override
    public void filter(ContainerRequestContext req) {
        String id = req.getHeaderString(HEADER);
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        ctx.set(id);
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext resp) {
        if (ctx.get() != null) {
            resp.getHeaders().putSingle(HEADER, ctx.get());
        }
    }
}
