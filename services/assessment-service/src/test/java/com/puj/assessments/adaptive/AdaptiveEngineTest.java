package com.puj.assessments.adaptive;

import com.puj.assessments.entity.AdaptiveRule;
import com.puj.assessments.repository.AdaptiveRuleRepository;
import com.puj.security.redis.RedisClientProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdaptiveEngineTest {

    @Mock private AdaptiveRuleRepository ruleRepo;
    @Mock private RedisClientProvider    redisProvider;
    @Mock private JedisPool              jedisPool;
    @Mock private Jedis                  jedis;

    @InjectMocks
    private AdaptiveEngine engine;

    private UUID assessmentId;
    private UUID userId;
    private AdaptiveRule rule;

    @BeforeEach
    void setUp() {
        assessmentId = UUID.randomUUID();
        userId       = UUID.randomUUID();

        rule = new AdaptiveRule();
        setField(rule, "id", UUID.randomUUID());
        rule.setAssessmentId(assessmentId);
        rule.setCourseId(UUID.randomUUID());
        rule.setScoreThresholdPct(60.0);
        rule.setSupplementaryLessonId(UUID.randomUUID());
        rule.setMessage("Repasa el material antes de continuar.");
        rule.setActive(true);

        when(redisProvider.getPool()).thenReturn(jedisPool);
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get(anyString())).thenReturn(null);
        doNothing().when(jedis).close();
    }

    @Test
    void evaluate_scoreBelow_returnsRecommendation() {
        when(ruleRepo.findByAssessment(assessmentId)).thenReturn(Optional.of(rule));

        Optional<AdaptiveRecommendation> result = engine.evaluate(
                assessmentId, userId,
                BigDecimal.valueOf(4), BigDecimal.valueOf(10)
        );

        assertThat(result).isPresent();
        assertThat(result.get().achievedScorePct()).isEqualTo(40.0);
        assertThat(result.get().requiredScorePct()).isEqualTo(60.0);
    }

    @Test
    void evaluate_scoreAbove_returnsEmpty() {
        when(ruleRepo.findByAssessment(assessmentId)).thenReturn(Optional.of(rule));

        Optional<AdaptiveRecommendation> result = engine.evaluate(
                assessmentId, userId,
                BigDecimal.valueOf(7), BigDecimal.valueOf(10)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_noRule_returnsEmpty() {
        when(ruleRepo.findByAssessment(assessmentId)).thenReturn(Optional.empty());

        Optional<AdaptiveRecommendation> result = engine.evaluate(
                assessmentId, userId,
                BigDecimal.valueOf(0), BigDecimal.valueOf(10)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_redisFails_fallbackToDb() {
        when(jedis.get(anyString())).thenThrow(new RuntimeException("Redis timeout"));
        when(ruleRepo.findByAssessment(assessmentId)).thenReturn(Optional.of(rule));

        Optional<AdaptiveRecommendation> result = engine.evaluate(
                assessmentId, userId,
                BigDecimal.valueOf(3), BigDecimal.valueOf(10)
        );

        assertThat(result).isPresent();
        verify(ruleRepo, times(1)).findByAssessment(assessmentId);
    }

    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
