package com.puj.colaboracion.salud;

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
public class RecursoSalud {

    @Inject private ProveedorRedis redis;

    /**
     * Sonda de liveness: indica que el proceso de la JVM está activo.
     *
     * @return respuesta 200 con estado UP
     */
    @GET
    @Path("/live")
    public Response vivo() {
        return Response.ok(
                Map.of("status", "UP", "service", "collaboration-service"))
                .build();
    }

    /**
     * Sonda de readiness: verifica que Redis está disponible.
     *
     * @return respuesta 200 (UP) o 503 (DEGRADED) con detalle de la dependencia
     */
    @GET
    @Path("/ready")
    public Response listo() {
        boolean redisDisponible = verificarRedis();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status",    redisDisponible ? "UP" : "DEGRADED");
        cuerpo.put("service",   "collaboration-service");
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("redis",     redisDisponible);

        return Response.status(redisDisponible ? 200 : 503).entity(cuerpo).build();
    }

    /**
     * Endpoint de retrocompatibilidad; delega al probe de readiness.
     *
     * @return la misma respuesta que {@link #listo()}
     */
    @GET
    public Response salud() { return listo(); }

    /**
     * Verifica la conectividad con Redis mediante un comando PING.
     *
     * @return {@code true} si Redis responde PONG, {@code false} en caso contrario
     */
    private boolean verificarRedis() {
        try (Jedis jedis = redis.obtenerPool().getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
