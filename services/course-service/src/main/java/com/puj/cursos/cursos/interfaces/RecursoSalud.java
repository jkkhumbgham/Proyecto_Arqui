package com.puj.cursos.cursos.interfaces;

import com.puj.eventos.publicador.ProveedorConexionRabbitMQ;
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
public class RecursoSalud {

    @Inject private ProveedorConexionRabbitMQ rabbitMQ;

    /**
     * Liveness probe: confirma que el proceso está en ejecución.
     *
     * @return respuesta 200 con {@code {"status":"UP","service":"course-service"}}
     */
    @GET
    @Path("/live")
    public Response liveness() {
        return Response.ok(Map.of("status", "UP", "service", "course-service")).build();
    }

    /**
     * Readiness probe: verifica que las dependencias externas están disponibles.
     *
     * @return respuesta 200 si todas las dependencias están disponibles, o 503 si no
     */
    @GET
    @Path("/ready")
    public Response readiness() {
        boolean mqOk = rabbitMQ != null && rabbitMQ.estaDisponible();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status",    mqOk ? "UP" : "DEGRADED");
        cuerpo.put("service",   "course-service");
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("rabbitMQ",  mqOk);

        return Response.status(mqOk ? 200 : 503).entity(cuerpo).build();
    }

    /**
     * Endpoint de retrocompatibilidad que delega en {@link #readiness()}.
     *
     * @return misma respuesta que {@link #readiness()}
     */
    @GET
    public Response salud() { return readiness(); }
}
