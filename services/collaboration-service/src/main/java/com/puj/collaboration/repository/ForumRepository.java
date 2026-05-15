package com.puj.collaboration.repository;

import com.puj.collaboration.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ForumRepository {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    public Forum save(Forum forum) {
        if (forum.getId() == null) { em.persist(forum); return forum; }
        return em.merge(forum);
    }

    public Optional<Forum> findById(UUID id) {
        return Optional.ofNullable(em.find(Forum.class, id))
                .filter(f -> !f.isDeleted());
    }

    public Optional<Forum> findByCourse(UUID courseId) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT f FROM Forum f WHERE f.courseId = :cid AND f.deletedAt IS NULL",
                            Forum.class)
                    .setParameter("cid", courseId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Thread saveThread(Thread thread) {
        em.persist(thread);
        return thread;
    }

    public Optional<Thread> findThreadById(UUID id) {
        return Optional.ofNullable(em.find(Thread.class, id))
                .filter(t -> !t.isDeleted());
    }

    public Post savePost(Post post) {
        if (post.getId() == null) { em.persist(post); return post; }
        return em.merge(post);
    }

    public Optional<Post> findPostById(UUID id) {
        return Optional.ofNullable(em.find(Post.class, id))
                .filter(p -> !p.isDeleted());
    }
}
