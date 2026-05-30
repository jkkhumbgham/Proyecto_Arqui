package com.puj.users.repository;

import com.puj.users.entity.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link RefreshToken}.
 *
 * <p>Gestiona la persistencia y revocación de refresh tokens. La revocación
 * masiva de tokens de un usuario se realiza mediante una actualización JPQL
 * directa para minimizar las operaciones de base de datos.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class RefreshTokenRepository {

    @PersistenceContext(unitName = "UserServicePU")
    private EntityManager em;

    /**
     * Persiste un nuevo refresh token en la base de datos.
     *
     * @param token refresh token a guardar; no debe ser {@code null}.
     * @return el mismo token después de la persistencia.
     */
    public RefreshToken save(RefreshToken token) {
        em.persist(token);
        return token;
    }

    /**
     * Busca un refresh token por su identificador.
     *
     * @param id UUID del refresh token.
     * @return {@link Optional} con el token si existe.
     */
    public Optional<RefreshToken> findById(UUID id) {
        return Optional.ofNullable(em.find(RefreshToken.class, id));
    }

    /**
     * Revoca todos los refresh tokens activos de un usuario.
     *
     * <p>Se utiliza durante el logout para invalidar cualquier sesión activa.
     * La operación actualiza directamente la columna {@code revoked_at} de
     * todos los tokens no revocados del usuario.
     *
     * @param userId UUID del usuario cuyos tokens serán revocados.
     */
    public void revokeAllForUser(UUID userId) {
        em.createQuery(
                "UPDATE RefreshToken rt"
                + " SET rt.revokedAt = CURRENT_TIMESTAMP"
                + " WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    /**
     * Sincroniza el estado de un refresh token gestionado con la base de datos.
     *
     * @param token entidad a sincronizar.
     * @return la entidad gestionada resultante del merge.
     */
    public RefreshToken merge(RefreshToken token) {
        return em.merge(token);
    }
}
