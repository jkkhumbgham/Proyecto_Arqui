package com.puj.security.redis;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Proveedor del pool de conexiones Jedis a Redis.
 *
 * <p>Singleton CDI que inicializa y gestiona el ciclo de vida del {@link JedisPool}.
 * Todos los componentes que necesitan acceder a Redis deben inyectar este bean
 * y obtener conexiones con {@code try-with-resources}:</p>
 *
 * <pre>{@code
 * try (Jedis jedis = redisProvider.getPool().getResource()) {
 *     jedis.set("key", "value");
 * }
 * }</pre>
 *
 * <p>Configuración del pool:</p>
 * <ul>
 *   <li>Máximo de conexiones: 20 (RNF-01f)</li>
 *   <li>Tiempo de espera para obtener conexión: 3 segundos</li>
 *   <li>Validación de conexión al obtener del pool</li>
 * </ul>
 *
 * <p>Variables de entorno requeridas: {@code REDIS_HOST}, {@code REDIS_PORT},
 * {@code REDIS_PASSWORD} (opcional).</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class RedisClientProvider {

    private static final Logger LOG = Logger.getLogger(RedisClientProvider.class.getName());

    private JedisPool pool;

    /**
     * Inicializa el pool de conexiones Jedis al arrancar el bean CDI.
     *
     * <p>Lee configuración desde variables de entorno. Si {@code REDIS_PASSWORD}
     * no está definida, se conecta sin autenticación.</p>
     */
    @PostConstruct
    void init() {
        String host     = getEnv("REDIS_HOST", "localhost");
        int    port     = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
        String password = System.getenv("REDIS_PASSWORD");

        JedisPoolConfig config = buildPoolConfig();

        this.pool = (password != null && !password.isBlank())
                ? new JedisPool(config, host, port, 3000, password)
                : new JedisPool(config, host, port);

        LOG.info("Redis pool initialized: " + host + ":" + port);
    }

    /**
     * Cierra el pool de conexiones al destruir el bean CDI (shutdown del servidor).
     */
    @PreDestroy
    void close() {
        if (pool != null) pool.close();
    }

    /**
     * Retorna el pool de conexiones Jedis para uso con {@code try-with-resources}.
     *
     * @return pool de conexiones a Redis; nunca {@code null} tras {@link #init()}
     */
    public JedisPool getPool() { return pool; }

    /**
     * Construye la configuración del pool Jedis con los parámetros del proyecto.
     *
     * @return configuración del pool Jedis
     */
    private JedisPoolConfig buildPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);
        config.setMaxIdle(5);
        config.setMinIdle(2);
        config.setTestOnBorrow(true);
        config.setMaxWait(Duration.ofSeconds(3));
        return config;
    }

    /**
     * Lee una variable de entorno con valor por defecto.
     *
     * @param key          nombre de la variable de entorno
     * @param defaultValue valor a usar si la variable no está definida o está vacía
     * @return valor de la variable o {@code defaultValue}
     */
    private String getEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
