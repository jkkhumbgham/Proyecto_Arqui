package com.puj.seguridad.listanegra;

import com.puj.seguridad.redis.ProveedorRedis;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestiona la lista negra de JWTs revocados usando Redis como almacén.
 *
 * <p>Cuando un usuario hace logout o un ADMIN desactiva su cuenta, el {@code jti}
 * del token activo se agrega a esta lista negra con un TTL igual al tiempo restante
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
 * @see com.puj.seguridad.jwt.FiltroJwt
 */
@ApplicationScoped
public class ServicioListaNegra {

    private static final Logger LOG    = Logger.getLogger(ServicioListaNegra.class.getName());
    private static final String PREFIJO = "jwt:blacklist:";

    @Inject
    private ProveedorRedis proveedorRedis;

    /**
     * Agrega un token a la lista negra con TTL automático.
     *
     * <p>Si {@code ttlSegundos} es {@code <= 0}, no se agrega ninguna entrada
     * (el token ya expiró o expiración indefinida).</p>
     *
     * @param jti         identificador único del token (claim {@code jti})
     * @param ttlSegundos segundos hasta que el token hubiera expirado; debe ser &gt; 0
     */
    public void agregarAListaNegra(String jti, long ttlSegundos) {
        if (ttlSegundos <= 0) return;
        try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
            jedis.setex(PREFIJO + jti, ttlSegundos, "1");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo agregar token a lista negra Redis: " + jti, e);
        }
    }

    /**
     * Verifica si un token está en la lista negra.
     *
     * <p>Retorna {@code false} si Redis no está disponible (fail-open), para
     * evitar que una caída de Redis bloquee el acceso de todos los usuarios.</p>
     *
     * @param jti identificador único del token a verificar
     * @return {@code true} si el token está revocado; {@code false} si es válido o Redis falla
     */
    public boolean estaEnListaNegra(String jti) {
        try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
            return jedis.exists(PREFIJO + jti);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "No se pudo verificar lista negra Redis — asumiendo no bloqueado", e);
            return false;
        }
    }
}
