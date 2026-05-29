package com.puj.users.rest;

import com.puj.events.publisher.RabbitMQConnectionProvider;
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

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject private RabbitMQConnectionProvider rabbitMQ;
    @Inject private RedisClientProvider        redis;

    /** Liveness: el proceso está vivo. Nunca verifica dependencias externas. */
    @GET
    @Path("/live")
    public Response live() {
        return Response.ok(Map.of("status", "UP", "service", "user-service")).build();
    }

    /** Readiness: el servicio está listo para recibir tráfico (dependencias OK). */
    @GET
    @Path("/ready")
    public Response ready() {
        boolean mqOk    = rabbitMQ != null && rabbitMQ.isAvailable();
        boolean redisOk = checkRedis();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    mqOk && redisOk ? "UP" : "DEGRADED");
        body.put("service",   "user-service");
        body.put("timestamp", Instant.now().toString());
        body.put("rabbitMQ",  mqOk);
        body.put("redis",     redisOk);

        int status = mqOk && redisOk ? 200 : 503;
        return Response.status(status).entity(body).build();
    }

    /** Retrocompatibilidad: redirige al endpoint ready. */
    @GET
    public Response health() { return ready(); }

    private boolean checkRedis() {
        try (Jedis jedis = redis.getPool().getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
