package com.puj.email.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    /** Liveness: el proceso está vivo. Nunca verifica dependencias externas. */
    @GET
    @Path("/live")
    public Response live() {
        return Response.ok(Map.of("status", "UP", "service", "email-service")).build();
    }

    /** Readiness: email-service no expone dependencias externas via HTTP;
     *  la conectividad SMTP se verifica en tiempo de envío (SmtpEmailService). */
    @GET
    @Path("/ready")
    public Response ready() {
        return Response.ok(Map.of(
                "status",    "UP",
                "service",   "email-service",
                "timestamp", Instant.now().toString()
        )).build();
    }

    /** Retrocompatibilidad. */
    @GET
    public Response health() { return ready(); }
}
