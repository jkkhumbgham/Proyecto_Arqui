package com.puj.seguridad.limitacion;

import com.puj.seguridad.redis.ProveedorRedis;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rate limiter de ventana deslizante basado en Redis.
 * Clave: rate:{ip}:{epoch_minuto} — expira automáticamente tras VENTANA_SEGUNDOS.
 * Requerido por RNF-03d: 100 req/min/IP en endpoints públicos.
 */
@ApplicationScoped
public class ServicioLimiteTasa {

    private static final Logger LOG             = Logger.getLogger(ServicioLimiteTasa.class.getName());
    private static final int    MAX_SOLICITUDES = 100;
    private static final int    VENTANA_SEGUNDOS = 60;

    @Inject
    private ProveedorRedis redis;

    /**
     * Retorna {@code true} si la solicitud está permitida, {@code false} si se superó el límite.
     * Ante fallo de Redis, permite la solicitud (fail-open) para no bloquear usuarios legítimos.
     *
     * @param ip dirección IP del cliente
     * @return {@code true} si la solicitud es permitida
     */
    public boolean estaPermitido(String ip) {
        try (Jedis jedis = redis.obtenerPool().getResource()) {
            String clave  = "rate:" + ip + ":" + (System.currentTimeMillis() / (VENTANA_SEGUNDOS * 1000L));
            long   cuenta = jedis.incr(clave);
            if (cuenta == 1) jedis.expire(clave, VENTANA_SEGUNDOS);
            return cuenta <= MAX_SOLICITUDES;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Rate limiter Redis no disponible — permitiendo solicitud", e);
            return true;
        }
    }
}
