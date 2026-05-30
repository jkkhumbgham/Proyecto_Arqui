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

/**
 * Recurso REST que expone los endpoints de salud del servicio de cursos.
 *
 * <p>Implementa el patrón de sondeo de Kubernetes con dos endpoints diferenciados:
 * <ul>
 *   <li>{@code /health/live}  — liveness probe: el proceso está vivo.</li>
 *   <li>{@code /health/ready} — readiness probe: las dependencias están disponibles.</li>
 * </ul>
 * El endpoint raíz {@code /health} se mantiene por retrocompatibilidad.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject private RabbitMQConnectionProvider rabbitMQ;

    /**
     * Liveness probe: confirma que el proceso está en ejecución.
     *
     * <p>Nunca verifica dependencias externas para evitar reinicios en cascada.
     *
     * @return respuesta 200 con {@code {"status":"UP","service":"course-service"}}
     */
    @GET
    @Path("/live")
    public Response live() {
        return Response.ok(Map.of("status", "UP", "service", "course-service")).build();
    }

    /**
     * Readiness probe: verifica que las dependencias externas están disponibles.
     *
     * <p>Comprueba la conectividad con RabbitMQ. Si no está disponible devuelve
     * HTTP 503 con estado {@code DEGRADED}.
     *
     * @return respuesta 200 si todas las dependencias están disponibles,
     *         o 503 si RabbitMQ no está accesible
     */
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

    /**
     * Endpoint de retrocompatibilidad que delega en {@link #ready()}.
     *
     * @return misma respuesta que {@link #ready()}
     */
    @GET
    public Response health() { return ready(); }
}
