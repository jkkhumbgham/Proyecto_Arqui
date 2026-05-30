package com.puj.users.dto;

/**
 * DTO de respuesta para las operaciones de inicio de sesión y renovación de token.
 *
 * <p>Contiene el access token JWT, el refresh token opaco, el tiempo de vida
 * del access token en segundos, el tipo de token (siempre {@code "Bearer"}) y
 * la información pública del usuario autenticado.
 *
 * @param accessToken   JWT de corta duración para autorizar peticiones.
 * @param refreshToken  token opaco de larga duración para renovar el access token.
 * @param expiresIn     tiempo de vida del access token en segundos.
 * @param tokenType     tipo de token; siempre {@code "Bearer"}.
 * @param user          datos públicos del usuario autenticado.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record LoginResponse(
        String      accessToken,
        String      refreshToken,
        long        expiresIn,
        String      tokenType,
        UserResponse user
) {
    /**
     * Crea una instancia de {@code LoginResponse} con tipo de token predeterminado
     * ({@code "Bearer"}).
     *
     * @param accessToken  JWT de acceso emitido.
     * @param refreshToken token de renovación emitido.
     * @param expiresIn    tiempo de vida del access token en segundos.
     * @param user         datos públicos del usuario autenticado.
     * @return nueva instancia de {@code LoginResponse}.
     */
    public static LoginResponse of(String accessToken, String refreshToken,
                                   long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, refreshToken, expiresIn, "Bearer", user);
    }
}
