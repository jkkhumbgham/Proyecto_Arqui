package com.puj.users.repository;

import com.puj.users.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link User}.
 *
 * <p>Todas las consultas excluyen automáticamente los registros con borrado
 * lógico ({@code deleted_at IS NOT NULL}), de modo que la capa de servicio no
 * necesita filtrar por ese campo.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    /**
     * Persiste o actualiza un usuario en la base de datos.
     *
     * <p>Si el usuario aún no tiene identificador se invoca {@code persist};
     * en caso contrario se utiliza {@code merge}.
     *
     * @param user entidad a persistir o actualizar.
     * @return la entidad gestionada resultante.
     */
    public User save(User user) {
        if (user.getId() == null) {
            em.persist(user);
            return user;
        }
        return em.merge(user);
    }

    /**
     * Busca un usuario activo (no eliminado) por su identificador.
     *
     * @param id UUID del usuario.
     * @return {@link Optional} con el usuario si existe y no ha sido eliminado.
     */
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(em.find(User.class, id))
                .filter(u -> !u.isDeleted());
    }

    /**
     * Busca un usuario activo por su correo electrónico.
     *
     * <p>La búsqueda normaliza el correo a minúsculas y elimina espacios antes
     * de comparar.
     *
     * @param email dirección de correo electrónico a buscar.
     * @return {@link Optional} con el usuario si existe y no ha sido eliminado.
     */
    public Optional<User> findByEmail(String email) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT u FROM User u"
                            + " WHERE u.email = :email AND u.deletedAt IS NULL",
                            User.class)
                    .setParameter("email", email.toLowerCase().trim())
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Devuelve una página de usuarios activos ordenados por fecha de creación
     * descendente.
     *
     * @param page número de página (basado en cero).
     * @param size número máximo de resultados por página.
     * @return lista de usuarios de la página solicitada.
     */
    public List<User> findAll(int page, int size) {
        return em.createQuery(
                        "SELECT u FROM User u"
                        + " WHERE u.deletedAt IS NULL"
                        + " ORDER BY u.createdAt DESC",
                        User.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /**
     * Cuenta el total de usuarios activos (no eliminados).
     *
     * @return número total de usuarios activos.
     */
    public long countAll() {
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL",
                        Long.class)
                .getSingleResult();
    }

    /**
     * Comprueba si ya existe un usuario activo con el correo electrónico dado.
     *
     * <p>La comparación es insensible a mayúsculas.
     *
     * @param email dirección de correo electrónico a verificar.
     * @return {@code true} si ya existe un usuario activo con ese correo.
     */
    public boolean existsByEmail(String email) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u"
                        + " WHERE LOWER(u.email) = LOWER(:email)"
                        + " AND u.deletedAt IS NULL",
                        Long.class)
                .setParameter("email", email)
                .getSingleResult() > 0;
    }

    /**
     * Devuelve una página de usuarios inactivos cuyo último inicio de sesión
     * es anterior al umbral indicado o que nunca han iniciado sesión.
     *
     * @param threshold instante límite de actividad; usuarios sin actividad
     *                  posterior a este instante se consideran inactivos.
     * @param page      número de página (basado en cero).
     * @param size      número máximo de resultados por página.
     * @return lista de usuarios inactivos.
     */
    public List<User> findInactive(Instant threshold, int page, int size) {
        return em.createQuery(
                        "SELECT u FROM User u"
                        + " WHERE u.deletedAt IS NULL"
                        + " AND (u.lastLoginAt IS NULL OR u.lastLoginAt < :threshold)"
                        + " ORDER BY u.lastLoginAt ASC NULLS FIRST",
                        User.class)
                .setParameter("threshold", threshold)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
