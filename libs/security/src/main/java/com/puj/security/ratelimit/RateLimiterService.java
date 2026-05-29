package com.puj.security.ratelimit;

import com.puj.security.redis.RedisClientProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis-based sliding-window rate limiter.
 * Key: rate:{ip}:{minute_epoch} — expires automatically after WINDOW_SECONDS.
 * Required by RNF-03d: 100 req/min/IP en endpoints públicos.
 */
@ApplicationScoped
public class RateLimiterService {

    private static final Logger LOG            = Logger.getLogger(RateLimiterService.class.getName());
    private static final int    MAX_REQUESTS   = 100;
    private static final int    WINDOW_SECONDS = 60;

    @Inject
    private RedisClientProvider redis;

    /**
     * Returns true if the request is allowed, false if the limit is exceeded.
     * On Redis failure, allows the request (fail-open) to avoid blocking legitimate users.
     */
    public boolean isAllowed(String ip) {
        try (Jedis jedis = redis.getPool().getResource()) {
            String key   = "rate:" + ip + ":" + (System.currentTimeMillis() / (WINDOW_SECONDS * 1000L));
            long   count = jedis.incr(key);
            if (count == 1) jedis.expire(key, WINDOW_SECONDS);
            return count <= MAX_REQUESTS;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Rate limiter Redis unavailable — allowing request", e);
            return true;
        }
    }
}
