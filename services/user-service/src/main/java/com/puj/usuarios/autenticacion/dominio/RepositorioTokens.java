package com.puj.usuarios.autenticacion.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de repositorio para la entidad {@link TokenRefresh}.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
public interface RepositorioTokens {

    /**
     * Persiste un nuevo refresh token.
     *
     * @param token refresh token a guardar.
     * @return el mismo token después de la persistencia.
     */
    TokenRefresh guardar(TokenRefresh token);

    /**
     * Busca un refresh token por su identificador.
     *
     * @param id UUID del refresh token.
     * @return {@link Optional} con el token si existe.
     */
    Optional<TokenRefresh> buscarPorId(UUID id);

    /**
     * Revoca todos los refresh tokens activos de un usuario.
     *
     * @param idUsuario UUID del usuario cuyos tokens serán revocados.
     */
    void revocarTodosDelUsuario(UUID idUsuario);

    /**
     * Sincroniza el estado de un refresh token gestionado con el almacén de datos.
     *
     * @param token entidad a sincronizar.
     * @return la entidad gestionada resultante.
     */
    TokenRefresh sincronizar(TokenRefresh token);
}
