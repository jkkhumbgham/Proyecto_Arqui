package com.puj.users.repository;

import com.puj.users.entity.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepository {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    public RefreshToken save(RefreshToken token) {
        em.persist(token);
        return token;
    }

    public Optional<RefreshToken> findById(UUID id) {
        return Optional.ofNullable(em.find(RefreshToken.class, id));
    }

    public void revokeAllForUser(UUID userId) {
        em.createQuery(
                "UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP " +
                "WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public RefreshToken merge(RefreshToken token) {
        return em.merge(token);
    }
}
