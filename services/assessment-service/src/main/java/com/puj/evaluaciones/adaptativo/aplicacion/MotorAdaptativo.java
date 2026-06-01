package com.puj.evaluaciones.adaptativo.aplicacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.evaluaciones.adaptativo.dominio.RecomendacionAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.ReglaAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.RepositorioReglasAdaptativas;
import com.puj.seguridad.redis.ProveedorRedis;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Motor adaptativo determinístico del servicio de evaluaciones.
 *
 * <p>Evalúa si el puntaje del estudiante se encuentra por debajo del umbral
 * configurado por el instructor y devuelve una recomendación de contenido
 * suplementario cuando corresponde.</p>
 *
 * <p>Flujo de resolución de la regla adaptativa:</p>
 * <ol>
 *   <li>Intentar leer la regla desde Redis (clave: {@code adaptive:rule:{idEvaluacion}},
 *       TTL de {@value #TTL_CACHE} segundos).</li>
 *   <li>Si Redis no está disponible, hacer fallback a PostgreSQL con log WARNING
 *       (código de error E-12).</li>
 *   <li>Evaluar: si {@code puntuacion < umbral} retornar una
 *       {@link RecomendacionAdaptativa} con la lección suplementaria configurada.</li>
 * </ol>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class MotorAdaptativo {

    private static final Logger LOG         = Logger.getLogger(MotorAdaptativo.class.getName());
    private static final String PREFIJO_KEY = "adaptive:rule:";
    private static final int    TTL_CACHE   = 60;

    @Inject private ProveedorRedis         redisProvider;
    @Inject private RepositorioReglasAdaptativas repoReglas;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Evalúa si el puntaje obtenido por el estudiante cae por debajo del umbral
     * definido en la regla adaptativa asociada a la evaluación.
     *
     * @param idEvaluacion identificador de la evaluación
     * @param idUsuario    identificador del estudiante que realizó la evaluación
     * @param puntuacion   puntaje bruto obtenido por el estudiante
     * @param puntuacionMax puntaje máximo posible en la evaluación
     * @return {@link Optional} con la recomendación si el puntaje está por debajo
     *         del umbral, o {@link Optional#empty()} si el estudiante aprobó o no
     *         existe una regla activa
     */
    public Optional<RecomendacionAdaptativa> evaluar(
            UUID idEvaluacion,
            UUID idUsuario,
            BigDecimal puntuacion,
            BigDecimal puntuacionMax) {

        Optional<ReglaAdaptativa> reglaOpt = encontrarRegla(idEvaluacion);
        if (reglaOpt.isEmpty()) return Optional.empty();

        ReglaAdaptativa regla = reglaOpt.get();
        if (!regla.isActiva()) return Optional.empty();

        double porcentajeObtenido = puntuacionMax.compareTo(BigDecimal.ZERO) > 0
                ? puntuacion.doubleValue() / puntuacionMax.doubleValue() * 100.0
                : 0.0;

        if (porcentajeObtenido < regla.getUmbralPorcentaje()) {
            String msg = regla.getMensaje() != null
                    ? regla.getMensaje()
                    : "Tu puntaje está por debajo del umbral. "
                    + "Te recomendamos repasar el material.";
            return Optional.of(new RecomendacionAdaptativa(
                    regla.getIdLeccionSupplementaria(),
                    msg,
                    porcentajeObtenido,
                    regla.getUmbralPorcentaje()
            ));
        }

        return Optional.empty();
    }

    /**
     * Invalida la entrada en caché de Redis para la regla adaptativa de una
     * evaluación. Se debe llamar tras crear, actualizar o eliminar una regla.
     *
     * @param idEvaluacion identificador de la evaluación cuya caché se invalida
     */
    public void invalidarCache(UUID idEvaluacion) {
        try (Jedis jedis = redisProvider.obtenerPool().getResource()) {
            jedis.del(PREFIJO_KEY + idEvaluacion);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "No se pudo invalidar caché de regla adaptativa", e);
        }
    }

    /**
     * Busca la regla adaptativa activa para una evaluación, intentando primero
     * Redis y recurriendo a PostgreSQL si Redis no está disponible.
     *
     * @param idEvaluacion identificador de la evaluación
     * @return {@link Optional} con la regla encontrada, o vacío si no existe
     */
    private Optional<ReglaAdaptativa> encontrarRegla(UUID idEvaluacion) {
        String cacheKey = PREFIJO_KEY + idEvaluacion;

        try (Jedis jedis = redisProvider.obtenerPool().getResource()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return Optional.of(mapper.readValue(cached, ReglaAdaptativa.class));
            }

            Optional<ReglaAdaptativa> regla = repoReglas.buscarPorEvaluacion(idEvaluacion);
            regla.ifPresent(r -> cachearRegla(jedis, cacheKey, r));
            return regla;

        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Redis no disponible — leyendo regla adaptativa desde "
                    + "PostgreSQL (fallback E-12)", e);
            return repoReglas.buscarPorEvaluacion(idEvaluacion);
        }
    }

    /**
     * Serializa y almacena una regla adaptativa en Redis con el TTL configurado.
     *
     * @param jedis    conexión Jedis activa
     * @param cacheKey clave Redis bajo la que se almacenará la regla
     * @param regla    regla adaptativa a serializar y almacenar
     */
    private void cachearRegla(Jedis jedis, String cacheKey, ReglaAdaptativa regla) {
        try {
            jedis.setex(cacheKey, TTL_CACHE, mapper.writeValueAsString(regla));
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "No se pudo cachear regla adaptativa en Redis", e);
        }
    }
}
