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
 * Motor adaptativo determinístico. Evalúa si el score del estudiante está
 * por debajo del umbral configurado por el instructor y devuelve la recomendación
 * de contenido suplementario si aplica.
 *
 * Flujo:
 *  1. Intentar leer regla de Redis (clave: adaptive:rule:{assessmentId}, TTL 60s).
 *  2. Si Redis falla → fallback a PostgreSQL con log WARNING (E-12).
 *  3. Evaluar: score < threshold → AdaptiveRecommendation con lessonId suplementario.
 */
@ApplicationScoped
public class AdaptiveEngine {

    private static final Logger LOG       = Logger.getLogger(AdaptiveEngine.class.getName());
    private static final String KEY_PREFIX = "adaptive:rule:";
    private static final int    CACHE_TTL  = 60;

    @Inject private RedisClientProvider    redisProvider;
    @Inject private AdaptiveRuleRepository ruleRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<AdaptiveRecommendation> evaluate(UUID assessmentId, UUID userId,
                                                      BigDecimal score, BigDecimal maxScore) {
        Optional<AdaptiveRule> ruleOpt = findRule(assessmentId);
        if (ruleOpt.isEmpty()) return Optional.empty();

        AdaptiveRule rule = ruleOpt.get();
        if (!rule.isActive()) return Optional.empty();

        double scorePct = maxScore.compareTo(BigDecimal.ZERO) > 0
                ? score.doubleValue() / maxScore.doubleValue() * 100.0
                : 0.0;

        if (scorePct < rule.getScoreThresholdPct()) {
            return Optional.of(new AdaptiveRecommendation(
                    rule.getSupplementaryLessonId(),
                    rule.getMessage() != null ? rule.getMessage()
                            : "Tu puntaje está por debajo del umbral. Te recomendamos repasar el material.",
                    scorePct,
                    rule.getScoreThresholdPct()
            ));
        }

        return Optional.empty();
    }

    private Optional<AdaptiveRule> findRule(UUID assessmentId) {
        String cacheKey = KEY_PREFIX + assessmentId;

        try (Jedis jedis = redisProvider.getPool().getResource()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return Optional.of(mapper.readValue(cached, AdaptiveRule.class));
            }

            Optional<AdaptiveRule> rule = ruleRepo.findByAssessment(assessmentId);
            rule.ifPresent(r -> {
                try {
                    jedis.setex(cacheKey, CACHE_TTL, mapper.writeValueAsString(r));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "No se pudo cachear regla adaptativa en Redis", e);
                }
            });
            return rule;

        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Redis no disponible — leyendo regla adaptativa desde PostgreSQL (fallback E-12)", e);
            return ruleRepo.findByAssessment(assessmentId);
        }
    }

    public void invalidateCache(UUID assessmentId) {
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            jedis.del(KEY_PREFIX + assessmentId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo invalidar caché de regla adaptativa", e);
        }
    }
}
