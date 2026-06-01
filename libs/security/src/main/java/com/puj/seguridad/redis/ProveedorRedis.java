package com.puj.seguridad.redis;

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
 * try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
 *     jedis.set("clave", "valor");
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
public class ProveedorRedis {

    private static final Logger LOG = Logger.getLogger(ProveedorRedis.class.getName());

    private JedisPool pool;

    /**
     * Inicializa el pool de conexiones Jedis al arrancar el bean CDI.
     *
     * <p>Lee configuración desde variables de entorno. Si {@code REDIS_PASSWORD}
     * no está definida, se conecta sin autenticación.</p>
     */
    @PostConstruct
    void init() {
        String host     = obtenerEnv("REDIS_HOST", "localhost");
        int    port     = Integer.parseInt(obtenerEnv("REDIS_PORT", "6379"));
        String password = System.getenv("REDIS_PASSWORD");

        JedisPoolConfig config = construirConfigPool();

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
    public JedisPool obtenerPool() { return pool; }

    /**
     * Construye la configuración del pool Jedis con los parámetros del proyecto.
     *
     * @return configuración del pool Jedis
     */
    private JedisPoolConfig construirConfigPool() {
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
     * @param clave        nombre de la variable de entorno
     * @param valorDefecto valor a usar si la variable no está definida o está vacía
     * @return valor de la variable o {@code valorDefecto}
     */
    private String obtenerEnv(String clave, String valorDefecto) {
        String v = System.getenv(clave);
        return (v != null && !v.isBlank()) ? v : valorDefecto;
    }
}
