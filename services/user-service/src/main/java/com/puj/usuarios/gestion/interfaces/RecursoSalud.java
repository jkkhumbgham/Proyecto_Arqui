package com.puj.usuarios.gestion.interfaces;

import com.puj.eventos.publicador.ProveedorConexionRabbitMQ;
import com.puj.seguridad.redis.ProveedorRedis;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recurso JAX-RS de salud del servicio de usuarios.
 *
 * <p>Expone dos sondas estándar de Kubernetes:
 * <ul>
 *   <li><strong>Liveness</strong> ({@code GET /health/live}) — indica que el
 *       proceso está vivo. Nunca verifica dependencias externas para evitar
 *       reinicios en cascada.</li>
 *   <li><strong>Readiness</strong> ({@code GET /health/ready}) — verifica que
 *       RabbitMQ y Redis están accesibles. Devuelve 503 si alguna dependencia
 *       no responde.</li>
 * </ul>
 *
 * <p>El endpoint {@code GET /health} (sin sufijo) es un alias de readiness
 * mantenido por compatibilidad retroactiva.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoSalud {

    @Inject private ProveedorConexionRabbitMQ rabbitMQ;
    @Inject private ProveedorRedis            redis;

    /**
     * Sonda de liveness: confirma que el proceso está en ejecución.
     *
     * <p>Siempre devuelve HTTP 200 mientras el proceso esté activo.
     *
     * @return respuesta HTTP 200 con estado {@code "UP"} y nombre del servicio.
     */
    @GET
    @Path("/live")
    public Response vivo() {
        return Response.ok(
                Map.of("status", "UP", "service", "user-service")).build();
    }

    /**
     * Sonda de readiness: verifica la disponibilidad de RabbitMQ y Redis.
     *
     * <p>Devuelve HTTP 200 cuando ambas dependencias responden correctamente,
     * o HTTP 503 ({@code "DEGRADED"}) cuando al menos una falla.
     *
     * @return respuesta con el estado de cada dependencia y la marca de tiempo.
     */
    @GET
    @Path("/ready")
    public Response listo() {
        boolean mqOk    = rabbitMQ != null && rabbitMQ.estaDisponible();
        boolean redisOk = verificarRedis();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status",    mqOk && redisOk ? "UP" : "DEGRADED");
        cuerpo.put("service",   "user-service");
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("rabbitMQ",  mqOk);
        cuerpo.put("redis",     redisOk);

        int estadoHttp = mqOk && redisOk ? 200 : 503;
        return Response.status(estadoHttp).entity(cuerpo).build();
    }

    /**
     * Alias del endpoint de readiness mantenido por compatibilidad retroactiva.
     *
     * @return misma respuesta que {@link #listo()}.
     */
    @GET
    public Response salud() {
        return listo();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Verifica la conectividad con Redis enviando un comando PING.
     *
     * @return {@code true} si Redis responde con {@code "PONG"};
     *         {@code false} si la conexión falla o el pool no está disponible.
     */
    private boolean verificarRedis() {
        try (Jedis jedis = redis.obtenerPool().getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
