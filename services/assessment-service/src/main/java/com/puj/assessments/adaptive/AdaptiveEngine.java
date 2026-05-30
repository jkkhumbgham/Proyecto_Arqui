package com.puj.assessments.adaptive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.assessments.entity.AdaptiveRule;
import com.puj.assessments.repository.AdaptiveRuleRepository;
import com.puj.security.redis.RedisClientProvider;
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
 *   <li>Intentar leer la regla desde Redis (clave: {@code adaptive:rule:{assessmentId}},
 *       TTL de {@value #CACHE_TTL} segundos).</li>
 *   <li>Si Redis no está disponible, hacer fallback a PostgreSQL con log WARNING
 *       (código de error E-12).</li>
 *   <li>Evaluar: si {@code score < threshold} retornar un
 *       {@link AdaptiveRecommendation} con la lección suplementaria configurada.</li>
 * </ol>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class AdaptiveEngine {

    private static final Logger LOG        = Logger.getLogger(AdaptiveEngine.class.getName());
    private static final String KEY_PREFIX = "adaptive:rule:";
    private static final int    CACHE_TTL  = 60;

    @Inject private RedisClientProvider    redisProvider;
    @Inject private AdaptiveRuleRepository ruleRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Evalúa si el puntaje obtenido por el estudiante cae por debajo del umbral
     * definido en la regla adaptativa asociada a la evaluación.
     *
     * @param assessmentId identificador de la evaluación
     * @param userId       identificador del estudiante que realizó la evaluación
     * @param score        puntaje bruto obtenido por el estudiante
     * @param maxScore     puntaje máximo posible en la evaluación
     * @return {@link Optional} con la recomendación si el puntaje está por debajo
     *         del umbral, o {@link Optional#empty()} si el estudiante aprobó o no
     *         existe una regla activa
     */
    public Optional<AdaptiveRecommendation> evaluate(
            UUID assessmentId,
            UUID userId,
            BigDecimal score,
            BigDecimal maxScore) {

        Optional<AdaptiveRule> ruleOpt = findRule(assessmentId);
        if (ruleOpt.isEmpty()) return Optional.empty();

        AdaptiveRule rule = ruleOpt.get();
        if (!rule.isActive()) return Optional.empty();

        double scorePct = maxScore.compareTo(BigDecimal.ZERO) > 0
                ? score.doubleValue() / maxScore.doubleValue() * 100.0
                : 0.0;

        if (scorePct < rule.getScoreThresholdPct()) {
            String msg = rule.getMessage() != null
                    ? rule.getMessage()
                    : "Tu puntaje está por debajo del umbral. "
                    + "Te recomendamos repasar el material.";
            return Optional.of(new AdaptiveRecommendation(
                    rule.getSupplementaryLessonId(),
                    msg,
                    scorePct,
                    rule.getScoreThresholdPct()
            ));
        }

        return Optional.empty();
    }

    /**
     * Invalida la entrada en caché de Redis para la regla adaptativa de una
     * evaluación. Se debe llamar tras crear, actualizar o eliminar una regla.
     *
     * @param assessmentId identificador de la evaluación cuya caché se invalida
     */
    public void invalidateCache(UUID assessmentId) {
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            jedis.del(KEY_PREFIX + assessmentId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "No se pudo invalidar caché de regla adaptativa", e);
        }
    }

    /**
     * Busca la regla adaptativa activa para una evaluación, intentando primero
     * Redis y recurriendo a PostgreSQL si Redis no está disponible.
     *
     * @param assessmentId identificador de la evaluación
     * @return {@link Optional} con la regla encontrada, o vacío si no existe
     */
    private Optional<AdaptiveRule> findRule(UUID assessmentId) {
        String cacheKey = KEY_PREFIX + assessmentId;

        try (Jedis jedis = redisProvider.getPool().getResource()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return Optional.of(mapper.readValue(cached, AdaptiveRule.class));
            }

            Optional<AdaptiveRule> rule = ruleRepo.findByAssessment(assessmentId);
            rule.ifPresent(r -> cacheRule(jedis, cacheKey, r));
            return rule;

        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Redis no disponible — leyendo regla adaptativa desde "
                    + "PostgreSQL (fallback E-12)", e);
            return ruleRepo.findByAssessment(assessmentId);
        }
    }

    /**
     * Serializa y almacena una regla adaptativa en Redis con el TTL configurado.
     *
     * @param jedis    conexión Jedis activa
     * @param cacheKey clave Redis bajo la que se almacenará la regla
     * @param rule     regla adaptativa a serializar y almacenar
     */
    private void cacheRule(Jedis jedis, String cacheKey, AdaptiveRule rule) {
        try {
            jedis.setex(cacheKey, CACHE_TTL, mapper.writeValueAsString(rule));
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "No se pudo cachear regla adaptativa en Redis", e);
        }
    }
}
