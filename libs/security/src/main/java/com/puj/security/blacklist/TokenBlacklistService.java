package com.puj.security.blacklist;

import com.puj.security.redis.RedisClientProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestiona la blacklist de JWTs revocados usando Redis como almacén.
 *
 * <p>Cuando un usuario hace logout o un ADMIN desactiva su cuenta, el {@code jti}
 * del token activo se agrega a esta blacklist con un TTL igual al tiempo restante
 * hasta su expiración natural. Así Redis libera la entrada automáticamente.</p>
 *
 * <p>La clave Redis tiene el formato {@code jwt:blacklist:{jti}}.</p>
 *
 * <p>Si Redis no está disponible, ambas operaciones fallan silenciosamente
 * (fail-open): se loggea un warning y se continúa sin bloquear la operación principal.
 * Esto prioriza disponibilidad sobre seguridad en escenarios de fallo de Redis.</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see com.puj.security.jwt.JwtFilter
 */
@ApplicationScoped
public class TokenBlacklistService {

    private static final Logger LOG    = Logger.getLogger(TokenBlacklistService.class.getName());
    private static final String PREFIX = "jwt:blacklist:";

    @Inject
    private RedisClientProvider redisProvider;

    /**
     * Agrega un token a la blacklist con TTL automático.
     *
     * <p>Si {@code ttlSeconds} es {@code <= 0}, no se agrega ninguna entrada
     * (el token ya expiró o expiración indefinida).</p>
     *
     * @param jti        identificador único del token (claim {@code jti})
     * @param ttlSeconds segundos hasta que el token hubiera expirado; debe ser &gt; 0
     */
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) return;
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            jedis.setex(PREFIX + jti, ttlSeconds, "1");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo agregar token a blacklist Redis: " + jti, e);
        }
    }

    /**
     * Verifica si un token está en la blacklist.
     *
     * <p>Retorna {@code false} si Redis no está disponible (fail-open), para
     * evitar que una caída de Redis bloquee el acceso de todos los usuarios.</p>
     *
     * @param jti identificador único del token a verificar
     * @return {@code true} si el token está revocado; {@code false} si es válido o Redis falla
     */
    public boolean isBlacklisted(String jti) {
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            return jedis.exists(PREFIX + jti);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "No se pudo verificar blacklist Redis — asumiendo no bloqueado", e);
            return false;
        }
    }
}
