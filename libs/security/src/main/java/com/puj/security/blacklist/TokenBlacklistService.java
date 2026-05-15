package com.puj.security.blacklist;

import com.puj.security.redis.RedisClientProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class TokenBlacklistService {

    private static final Logger LOG    = Logger.getLogger(TokenBlacklistService.class.getName());
    private static final String PREFIX = "jwt:blacklist:";

    @Inject
    private RedisClientProvider redisProvider;

    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) return;
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            jedis.setex(PREFIX + jti, ttlSeconds, "1");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo agregar token a blacklist Redis: " + jti, e);
        }
    }

    public boolean isBlacklisted(String jti) {
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            return jedis.exists(PREFIX + jti);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo verificar blacklist Redis — asumiendo no bloqueado", e);
            return false;
        }
    }
}
