package com.puj.courses.rest;

import com.puj.events.publisher.RabbitMQConnectionProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject private RabbitMQConnectionProvider rabbitMQ;

    /** Liveness: el proceso está vivo. Nunca verifica dependencias externas. */
    @GET
    @Path("/live")
    public Response live() {
        return Response.ok(Map.of("status", "UP", "service", "course-service")).build();
    }

    /** Readiness: el servicio está listo para recibir tráfico (dependencias OK). */
    @GET
    @Path("/ready")
    public Response ready() {
        boolean mqOk = rabbitMQ != null && rabbitMQ.isAvailable();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    mqOk ? "UP" : "DEGRADED");
        body.put("service",   "course-service");
        body.put("timestamp", Instant.now().toString());
        body.put("rabbitMQ",  mqOk);

        return Response.status(mqOk ? 200 : 503).entity(body).build();
    }

    /** Retrocompatibilidad: redirige al endpoint ready. */
    @GET
    public Response health() { return ready(); }
}
