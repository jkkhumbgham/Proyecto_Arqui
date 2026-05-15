package com.puj.users.repository;

import com.puj.users.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    public User save(User user) {
        if (user.getId() == null) {
            em.persist(user);
            return user;
        }
        return em.merge(user);
    }

    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(em.find(User.class, id))
                .filter(u -> !u.isDeleted());
    }

    public Optional<User> findByEmail(String email) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL",
                            User.class)
                    .setParameter("email", email.toLowerCase().trim())
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public List<User> findAll(int page, int size) {
        return em.createQuery(
                        "SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.createdAt DESC",
                        User.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public long countAll() {
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL", Long.class)
                .getSingleResult();
    }

    public boolean existsByEmail(String email) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL",
                        Long.class)
                .setParameter("email", email)
                .getSingleResult() > 0;
    }
}
