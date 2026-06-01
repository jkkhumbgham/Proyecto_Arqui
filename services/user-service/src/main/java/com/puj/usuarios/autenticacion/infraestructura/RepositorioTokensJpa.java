package com.puj.usuarios.autenticacion.infraestructura;

import com.puj.usuarios.autenticacion.dominio.RepositorioTokens;
import com.puj.usuarios.autenticacion.dominio.TokenRefresh;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA de {@link RepositorioTokens}.
 *
 * <p>Gestiona la persistencia y revocación de refresh tokens. La revocación
 * masiva de tokens de un usuario se realiza mediante una actualización JPQL
 * directa para minimizar las operaciones de base de datos.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class RepositorioTokensJpa implements RepositorioTokens {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    /** {@inheritDoc} */
    @Override
    public TokenRefresh guardar(TokenRefresh token) {
        em.persist(token);
        return token;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TokenRefresh> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(TokenRefresh.class, id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Se utiliza durante el cierre de sesión para invalidar cualquier sesión
     * activa. La operación actualiza directamente la columna {@code revoked_at}
     * de todos los tokens no revocados del usuario.
     */
    @Override
    public void revocarTodosDelUsuario(UUID idUsuario) {
        em.createQuery(
                "UPDATE TokenRefresh t"
                + " SET t.revocadoEn = CURRENT_TIMESTAMP"
                + " WHERE t.usuario.id = :idUsuario AND t.revocadoEn IS NULL")
                .setParameter("idUsuario", idUsuario)
                .executeUpdate();
    }

    /** {@inheritDoc} */
    @Override
    public TokenRefresh sincronizar(TokenRefresh token) {
        return em.merge(token);
    }
}
