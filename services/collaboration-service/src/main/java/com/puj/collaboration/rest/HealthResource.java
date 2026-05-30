package com.puj.collaboration.rest;

import com.puj.security.redis.RedisClientProvider;
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
 * Recurso REST de health-check del servicio de colaboración.
 *
 * <p>Expone tres endpoints siguiendo el patrón liveness/readiness de Kubernetes:</p>
 * <ul>
 *   <li>{@code /health/live} — liveness: el proceso está vivo (nunca verifica deps).</li>
 *   <li>{@code /health/ready} — readiness: verifica la conectividad con Redis.</li>
 *   <li>{@code /health} — retrocompatibilidad; delega a {@code /health/ready}.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject private RedisClientProvider redis;

    /**
     * Liveness probe: indica que el proceso de la JVM está activo.
     *
     * <p>No verifica dependencias externas; solo confirma que el servidor
     * está respondiendo peticiones HTTP.</p>
     *
     * @return respuesta 200 con estado UP
     */
    @GET
    @Path("/live")
    public Response live() {
        return Response.ok(
                Map.of("status", "UP", "service", "collaboration-service"))
                .build();
    }

    /**
     * Readiness probe: verifica que Redis está disponible.
     *
     * <p>Si Redis no responde, retorna estado {@code DEGRADED} con código 503.</p>
     *
     * @return respuesta 200 (UP) o 503 (DEGRADED) con detalle de la dependencia
     */
    @GET
    @Path("/ready")
    public Response ready() {
        boolean redisOk = checkRedis();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    redisOk ? "UP" : "DEGRADED");
        body.put("service",   "collaboration-service");
        body.put("timestamp", Instant.now().toString());
        body.put("redis",     redisOk);

        return Response.status(redisOk ? 200 : 503).entity(body).build();
    }

    /**
     * Endpoint de retrocompatibilidad; delega al probe de readiness.
     *
     * @return la misma respuesta que {@link #ready()}
     */
    @GET
    public Response health() { return ready(); }

    /**
     * Verifica la conectividad con Redis mediante un comando PING.
     *
     * @return {@code true} si Redis responde PONG, {@code false} en caso contrario
     */
    private boolean checkRedis() {
        try (Jedis jedis = redis.getPool().getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
