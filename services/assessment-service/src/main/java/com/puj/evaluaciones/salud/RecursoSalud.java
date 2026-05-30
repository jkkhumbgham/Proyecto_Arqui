package com.puj.evaluaciones.salud;

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
 * Recurso REST de health-check del servicio de evaluaciones.
 *
 * <p>Expone tres endpoints siguiendo el patrón liveness/readiness de Kubernetes:</p>
 * <ul>
 *   <li>{@code /health/live} — liveness: el proceso está vivo (nunca verifica deps).</li>
 *   <li>{@code /health/ready} — readiness: las dependencias externas (RabbitMQ, Redis)
 *       están disponibles.</li>
 *   <li>{@code /health} — retrocompatibilidad; delega a {@code /health/ready}.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class RecursoSalud {

    @Inject private ProveedorConexionRabbitMQ rabbitMQ;
    @Inject private ProveedorRedis        redis;

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
                Map.of("status", "UP", "service", "assessment-service"))
                .build();
    }

    /**
     * Readiness probe: verifica que las dependencias externas están disponibles.
     *
     * <p>Comprueba la conectividad con RabbitMQ y Redis. Si alguna dependencia
     * falla, retorna estado {@code DEGRADED} con código HTTP 503.</p>
     *
     * @return respuesta 200 (UP) o 503 (DEGRADED) con detalle de dependencias
     */
    @GET
    @Path("/ready")
    public Response ready() {
        boolean mqOk    = rabbitMQ != null && rabbitMQ.estaDisponible();
        boolean redisOk = verificarRedis();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status",    mqOk && redisOk ? "UP" : "DEGRADED");
        cuerpo.put("service",   "assessment-service");
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("rabbitMQ",  mqOk);
        cuerpo.put("redis",     redisOk);

        int codigoHttp = mqOk && redisOk ? 200 : 503;
        return Response.status(codigoHttp).entity(cuerpo).build();
    }

    /**
     * Endpoint de retrocompatibilidad; delega al probe de readiness.
     *
     * @return la misma respuesta que {@link #ready()}
     */
    @GET
    public Response salud() { return ready(); }

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
