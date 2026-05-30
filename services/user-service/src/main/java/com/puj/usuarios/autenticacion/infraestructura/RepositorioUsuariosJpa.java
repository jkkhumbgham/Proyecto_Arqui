package com.puj.usuarios.autenticacion.infraestructura;

import com.puj.usuarios.autenticacion.dominio.RepositorioUsuarios;
import com.puj.usuarios.autenticacion.dominio.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA de {@link RepositorioUsuarios}.
 *
 * <p>Todas las consultas excluyen automáticamente los registros con borrado
 * lógico ({@code deleted_at IS NOT NULL}), de modo que la capa de servicio no
 * necesita filtrar por ese campo.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class RepositorioUsuariosJpa implements RepositorioUsuarios {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    /**
     * {@inheritDoc}
     *
     * <p>Si el usuario aún no tiene identificador se invoca {@code persist};
     * en caso contrario se utiliza {@code merge}.
     */
    @Override
    public Usuario guardar(Usuario usuario) {
        if (usuario.getId() == null) {
            em.persist(usuario);
            return usuario;
        }
        return em.merge(usuario);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(Usuario.class, id))
                .filter(u -> !u.estaEliminado());
    }

    /**
     * {@inheritDoc}
     *
     * <p>La búsqueda normaliza el correo a minúsculas y elimina espacios antes
     * de comparar.
     */
    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT u FROM Usuario u"
                            + " WHERE u.correo = :correo AND u.eliminadoEn IS NULL",
                            Usuario.class)
                    .setParameter("correo", correo.toLowerCase().trim())
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resultados ordenados por fecha de creación descendente.
     */
    @Override
    public List<Usuario> buscarTodos(int pagina, int cantidad) {
        return em.createQuery(
                        "SELECT u FROM Usuario u"
                        + " WHERE u.eliminadoEn IS NULL"
                        + " ORDER BY u.creadoEn DESC",
                        Usuario.class)
                .setFirstResult(pagina * cantidad)
                .setMaxResults(cantidad)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public long contarTodos() {
        return em.createQuery(
                        "SELECT COUNT(u) FROM Usuario u WHERE u.eliminadoEn IS NULL",
                        Long.class)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     *
     * <p>La comparación es insensible a mayúsculas.
     */
    @Override
    public boolean existePorCorreo(String correo) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM Usuario u"
                        + " WHERE LOWER(u.correo) = LOWER(:correo)"
                        + " AND u.eliminadoEn IS NULL",
                        Long.class)
                .setParameter("correo", correo)
                .getSingleResult() > 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Devuelve usuarios cuyo último acceso es anterior al umbral indicado
     * o que nunca han iniciado sesión, ordenados ascendentemente.
     */
    @Override
    public List<Usuario> buscarInactivos(Instant umbral, int pagina, int cantidad) {
        return em.createQuery(
                        "SELECT u FROM Usuario u"
                        + " WHERE u.eliminadoEn IS NULL"
                        + " AND (u.ultimoAcceso IS NULL OR u.ultimoAcceso < :umbral)"
                        + " ORDER BY u.ultimoAcceso ASC NULLS FIRST",
                        Usuario.class)
                .setParameter("umbral", umbral)
                .setFirstResult(pagina * cantidad)
                .setMaxResults(cantidad)
                .getResultList();
    }
}
