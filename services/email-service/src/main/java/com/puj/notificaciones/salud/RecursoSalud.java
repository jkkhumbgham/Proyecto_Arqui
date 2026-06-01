package com.puj.notificaciones.salud;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;

/**
 * Recurso JAX-RS que expone los endpoints de salud del servicio de correo.
 *
 * <p>Proporciona dos endpoints diferenciados según el modelo de sondas de
 * Kubernetes:</p>
 * <ul>
 *   <li><strong>Liveness</strong> ({@code GET /health/live}) — confirma que
 *       el proceso está en ejecución. Nunca verifica dependencias externas para
 *       evitar que una caída de RabbitMQ o del servidor SMTP provoque el
 *       reinicio del pod.</li>
 *   <li><strong>Readiness</strong> ({@code GET /health/ready}) — indica que el
 *       servicio está listo para recibir tráfico.</li>
 * </ul>
 *
 * <p>El endpoint {@code GET /health} se mantiene por retrocompatibilidad y
 * delega en {@link #listo()}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoSalud {

    /**
     * Sonda de liveness: confirma que el proceso está en ejecución.
     *
     * @return respuesta HTTP 200 con cuerpo {@code {"status":"UP","service":"email-service"}}.
     */
    @GET
    @Path("/live")
    public Response vivo() {
        return Response.ok(
                Map.of("status", "UP", "service", "email-service")).build();
    }

    /**
     * Sonda de readiness: indica que el servicio puede atender peticiones.
     *
     * @return respuesta HTTP 200 con cuerpo JSON que incluye {@code status},
     *         {@code service} y {@code timestamp} en formato ISO-8601.
     */
    @GET
    @Path("/ready")
    public Response listo() {
        return Response.ok(Map.of(
                "status",    "UP",
                "service",   "email-service",
                "timestamp", Instant.now().toString()
        )).build();
    }

    /**
     * Endpoint de salud por retrocompatibilidad; delega en {@link #listo()}.
     *
     * @return la misma respuesta que {@link #listo()}.
     */
    @GET
    public Response salud() {
        return listo();
    }
}
