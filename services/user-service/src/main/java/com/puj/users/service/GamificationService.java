package com.puj.users.service;

import com.puj.security.redis.RedisClientProvider;
import com.puj.users.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.logging.Logger;

@ApplicationScoped
public class GamificationService {

    private static final Logger LOG = Logger.getLogger(GamificationService.class.getName());

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    @Inject
    private RedisClientProvider redisProvider;

    private static final int RULES_CACHE_TTL = 60;

    public record PointsDTO(UUID userId, int totalPoints) {}
    public record BadgeDTO(String badgeCode, String description, String earnedAt) {}

    @Transactional
    public void awardPoints(UUID userId, String actionType, String referenceId) {
        int pts = getPoints(actionType);
        if (pts <= 0) return;

        PointEvent pe = new PointEvent();
        pe.setUserId(userId);
        pe.setActionType(actionType);
        pe.setPoints(pts);
        pe.setReferenceId(referenceId);
        em.persist(pe);

        UserPoints up;
        try {
            up = em.createQuery(
                    "SELECT u FROM UserPoints u WHERE u.userId = :uid", UserPoints.class)
                    .setParameter("uid", userId).getSingleResult();
            up.addPoints(pts);
            em.merge(up);
        } catch (NoResultException e) {
            up = new UserPoints();
            up.setUserId(userId);
            up.addPoints(pts);
            em.persist(up);
        }

        LOG.info("Gamification: +" + pts + " pts (" + actionType + ") user=" + userId);
        evaluateBadges(userId, up.getTotalPoints());
    }

    @Transactional
    public void awardAssessmentPoints(UUID userId, String assessmentId, boolean passed) {
        if (!passed) return;

        boolean alreadyPassedFirst;
        try {
            em.createQuery(
                    "SELECT p FROM PointEvent p WHERE p.userId = :uid AND p.referenceId = :ref " +
                    "AND p.actionType = 'ASSESSMENT_PASSED_FIRST'", PointEvent.class)
                    .setParameter("uid", userId).setParameter("ref", assessmentId)
                    .getSingleResult();
            alreadyPassedFirst = true;
        } catch (NoResultException e) {
            alreadyPassedFirst = false;
        }

        String action = alreadyPassedFirst ? "ASSESSMENT_PASSED_RETRY" : "ASSESSMENT_PASSED_FIRST";
        awardPoints(userId, action, assessmentId);
    }

    public PointsDTO getPoints(UUID userId) {
        try {
            UserPoints up = em.createQuery(
                    "SELECT u FROM UserPoints u WHERE u.userId = :uid", UserPoints.class)
                    .setParameter("uid", userId).getSingleResult();
            return new PointsDTO(userId, up.getTotalPoints());
        } catch (NoResultException e) {
            return new PointsDTO(userId, 0);
        }
    }

    public List<BadgeDTO> getBadges(UUID userId) {
        return em.createQuery(
                "SELECT b FROM UserBadge b WHERE b.userId = :uid ORDER BY b.earnedAt DESC",
                UserBadge.class)
                .setParameter("uid", userId)
                .getResultList()
                .stream()
                .map(b -> new BadgeDTO(b.getBadgeCode(), badgeDescription(b.getBadgeCode()), b.getEarnedAt().toString()))
                .toList();
    }

    private void evaluateBadges(UUID userId, int totalPoints) {
        Map<String, Integer> thresholds = Map.of(
                "PRIMER_PUNTO",    1,
                "EXPLORADOR",    100,
                "DEDICADO",      500,
                "EXPERTO",      1000,
                "MAESTRO",      5000
        );
        for (Map.Entry<String, Integer> e : thresholds.entrySet()) {
            if (totalPoints >= e.getValue()) {
                grantBadgeIfAbsent(userId, e.getKey());
            }
        }
    }

    private void grantBadgeIfAbsent(UUID userId, String badgeCode) {
        try {
            em.createQuery(
                    "SELECT b FROM UserBadge b WHERE b.userId = :uid AND b.badgeCode = :code",
                    UserBadge.class)
                    .setParameter("uid", userId).setParameter("code", badgeCode)
                    .getSingleResult();
        } catch (NoResultException e) {
            UserBadge badge = new UserBadge();
            badge.setUserId(userId);
            badge.setBadgeCode(badgeCode);
            em.persist(badge);
            LOG.info("Badge granted: " + badgeCode + " → user=" + userId);
        }
    }

    private int getPoints(String actionType) {
        String cacheKey = "gamification:rule:" + actionType;
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) return Integer.parseInt(cached);
        } catch (Exception ignored) {}

        try {
            GamificationRule rule = em.find(GamificationRule.class, actionType);
            if (rule == null || !rule.isActive()) return 0;
            try (Jedis jedis = redisProvider.getPool().getResource()) {
                jedis.setex(cacheKey, RULES_CACHE_TTL, String.valueOf(rule.getPoints()));
            } catch (Exception ignored) {}
            return rule.getPoints();
        } catch (Exception e) {
            return 0;
        }
    }

    private String badgeDescription(String code) {
        return switch (code) {
            case "PRIMER_PUNTO" -> "¡Primer punto ganado!";
            case "EXPLORADOR"   -> "Explorador: 100 puntos";
            case "DEDICADO"     -> "Dedicado: 500 puntos";
            case "EXPERTO"      -> "Experto: 1000 puntos";
            case "MAESTRO"      -> "Maestro: 5000 puntos";
            default             -> code;
        };
    }
}
