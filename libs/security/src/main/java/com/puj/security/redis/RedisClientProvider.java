package com.puj.security.redis;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.logging.Logger;

@ApplicationScoped
public class RedisClientProvider {

    private static final Logger LOG = Logger.getLogger(RedisClientProvider.class.getName());

    private JedisPool pool;

    @PostConstruct
    void init() {
        String host     = getEnv("REDIS_HOST", "localhost");
        int    port     = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
        String password = System.getenv("REDIS_PASSWORD");

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);
        config.setMaxIdle(5);
        config.setMinIdle(2);
        config.setTestOnBorrow(true);
        config.setMaxWait(Duration.ofSeconds(3));

        this.pool = (password != null && !password.isBlank())
                ? new JedisPool(config, host, port, 3000, password)
                : new JedisPool(config, host, port);

        LOG.info("Redis pool initialized: " + host + ":" + port);
    }

    @PreDestroy
    void close() {
        if (pool != null) pool.close();
    }

    public JedisPool getPool() { return pool; }

    private String getEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
