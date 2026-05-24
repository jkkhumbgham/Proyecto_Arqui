package com.puj.collaboration.repository;

import com.puj.collaboration.entity.*;
import com.puj.collaboration.entity.Thread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
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

    public List<Forum> findAll() {
        return em.createQuery(
                "SELECT f FROM Forum f WHERE f.deletedAt IS NULL ORDER BY f.createdAt DESC",
                Forum.class).getResultList();
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

    public Thread mergeThread(Thread thread) {
        return em.merge(thread);
    }

    public Optional<Thread> findThreadById(UUID id) {
        return Optional.ofNullable(em.find(Thread.class, id))
                .filter(t -> !t.isDeleted());
    }

    /** Returns [Thread, postCount] pairs for a given forum, ordered pinned first then newest. */
    public List<Object[]> findThreadsWithPostCount(UUID forumId) {
        return em.createQuery(
                "SELECT t, (SELECT COUNT(p) FROM Post p WHERE p.thread = t AND p.deletedAt IS NULL)" +
                " FROM Thread t WHERE t.forum.id = :fid AND t.deletedAt IS NULL" +
                " ORDER BY t.pinned DESC, t.createdAt DESC",
                Object[].class)
                .setParameter("fid", forumId).getResultList();
    }

    public List<Post> findPostsByThread(UUID threadId) {
        return em.createQuery(
                "SELECT p FROM Post p WHERE p.thread.id = :tid AND p.deletedAt IS NULL" +
                " ORDER BY p.createdAt ASC",
                Post.class)
                .setParameter("tid", threadId).getResultList();
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
