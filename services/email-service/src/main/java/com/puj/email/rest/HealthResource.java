package com.puj.email.rest;

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
 *       servicio está listo para recibir tráfico. La conectividad SMTP se
 *       comprueba en tiempo de envío dentro de {@link com.puj.email.service.SmtpEmailService},
 *       por lo que este endpoint no la verifica activamente.</li>
 * </ul>
 *
 * <p>El endpoint {@code GET /health} se mantiene por retrocompatibilidad y
 * delega en {@link #ready()}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    /**
     * Sonda de liveness: confirma que el proceso está en ejecución.
     *
     * <p>Responde siempre con HTTP 200 y un cuerpo JSON indicando el estado
     * {@code UP}. No verifica dependencias externas (RabbitMQ, SMTP) para
     * evitar reinicios del pod ante fallos transitorios de dichos servicios.</p>
     *
     * @return respuesta HTTP 200 con cuerpo {@code {"status":"UP","service":"email-service"}}.
     */
    @GET
    @Path("/live")
    public Response live() {
        return Response.ok(Map.of("status", "UP", "service", "email-service")).build();
    }

    /**
     * Sonda de readiness: indica que el servicio puede atender peticiones.
     *
     * <p>El email-service no expone dependencias externas a través de HTTP;
     * la conectividad SMTP se verifica en tiempo de envío dentro de
     * {@link com.puj.email.service.SmtpEmailService}. Por ello, este endpoint
     * responde {@code UP} siempre que el proceso esté activo, incluyendo
     * la marca de tiempo actual para facilitar el diagnóstico.</p>
     *
     * @return respuesta HTTP 200 con cuerpo JSON que incluye {@code status},
     *         {@code service} y {@code timestamp} en formato ISO-8601.
     */
    @GET
    @Path("/ready")
    public Response ready() {
        return Response.ok(Map.of(
                "status",    "UP",
                "service",   "email-service",
                "timestamp", Instant.now().toString()
        )).build();
    }

    /**
     * Endpoint de salud por retrocompatibilidad; delega en {@link #ready()}.
     *
     * @return la misma respuesta que {@link #ready()}.
     */
    @GET
    public Response health() {
        return ready();
    }
}
