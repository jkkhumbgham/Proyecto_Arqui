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

/**
 * Repositorio JPA para las entidades de foro: {@link Forum}, {@link Thread} y {@link Post}.
 *
 * <p>Todas las consultas de listado excluyen registros eliminados lógicamente
 * ({@code deletedAt IS NULL}). La consulta de hilos con conteo de posts utiliza
 * una subconsulta correlacionada para obtener el número de respuestas activas
 * de cada hilo.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class ForumRepository {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    /**
     * Persiste o actualiza un foro.
     *
     * @param forum foro a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    public Forum save(Forum forum) {
        if (forum.getId() == null) { em.persist(forum); return forum; }
        return em.merge(forum);
    }

    /**
     * Busca un foro por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del foro
     * @return {@link Optional} con el foro, o vacío si no existe o está eliminado
     */
    public Optional<Forum> findById(UUID id) {
        return Optional.ofNullable(em.find(Forum.class, id))
                .filter(f -> !f.isDeleted());
    }

    /**
     * Retorna todos los foros activos ordenados por fecha de creación descendente.
     *
     * @return lista de foros activos (puede estar vacía)
     */
    public List<Forum> findAll() {
        return em.createQuery(
                "SELECT f FROM Forum f "
                + "WHERE f.deletedAt IS NULL "
                + "ORDER BY f.createdAt DESC",
                Forum.class).getResultList();
    }

    /**
     * Busca el foro activo de un curso.
     *
     * @param courseId UUID del curso
     * @return {@link Optional} con el foro del curso, o vacío si no existe
     */
    public Optional<Forum> findByCourse(UUID courseId) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT f FROM Forum f "
                            + "WHERE f.courseId = :cid AND f.deletedAt IS NULL",
                            Forum.class)
                    .setParameter("cid", courseId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Persiste un nuevo hilo (INSERT).
     *
     * @param thread hilo a persistir
     * @return la misma instancia administrada por JPA
     */
    public Thread saveThread(Thread thread) {
        em.persist(thread);
        return thread;
    }

    /**
     * Actualiza un hilo existente (UPDATE/merge).
     *
     * @param thread hilo a actualizar
     * @return la instancia administrada resultante del merge
     */
    public Thread mergeThread(Thread thread) {
        return em.merge(thread);
    }

    /**
     * Busca un hilo por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del hilo
     * @return {@link Optional} con el hilo, o vacío si no existe o está eliminado
     */
    public Optional<Thread> findThreadById(UUID id) {
        return Optional.ofNullable(em.find(Thread.class, id))
                .filter(t -> !t.isDeleted());
    }

    /**
     * Retorna los hilos activos de un foro con el conteo de posts activos por hilo.
     *
     * <p>Los hilos fijados ({@code pinned}) aparecen primero, luego por fecha
     * de creación descendente.</p>
     *
     * @param forumId UUID del foro
     * @return lista de pares {@code [Thread, postCount]} donde {@code postCount}
     *         es un {@code Long}
     */
    public List<Object[]> findThreadsWithPostCount(UUID forumId) {
        return em.createQuery(
                "SELECT t, "
                + "(SELECT COUNT(p) FROM Post p "
                + " WHERE p.thread = t AND p.deletedAt IS NULL) "
                + "FROM Thread t "
                + "WHERE t.forum.id = :fid AND t.deletedAt IS NULL "
                + "ORDER BY t.pinned DESC, t.createdAt DESC",
                Object[].class)
                .setParameter("fid", forumId).getResultList();
    }

    /**
     * Retorna los posts activos de un hilo ordenados por fecha de creación ascendente.
     *
     * @param threadId UUID del hilo
     * @return lista de posts activos del hilo (puede estar vacía)
     */
    public List<Post> findPostsByThread(UUID threadId) {
        return em.createQuery(
                "SELECT p FROM Post p "
                + "WHERE p.thread.id = :tid AND p.deletedAt IS NULL "
                + "ORDER BY p.createdAt ASC",
                Post.class)
                .setParameter("tid", threadId).getResultList();
    }

    /**
     * Persiste o actualiza un post.
     *
     * @param post post a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    public Post savePost(Post post) {
        if (post.getId() == null) { em.persist(post); return post; }
        return em.merge(post);
    }

    /**
     * Busca un post por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del post
     * @return {@link Optional} con el post, o vacío si no existe o está eliminado
     */
    public Optional<Post> findPostById(UUID id) {
        return Optional.ofNullable(em.find(Post.class, id))
                .filter(p -> !p.isDeleted());
    }
}
