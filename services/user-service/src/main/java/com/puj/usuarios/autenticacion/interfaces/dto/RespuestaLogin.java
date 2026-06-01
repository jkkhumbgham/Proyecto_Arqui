package com.puj.usuarios.autenticacion.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de respuesta para las operaciones de inicio de sesión y renovación de token.
 *
 * <p>Contiene el access token JWT, el refresh token opaco, el tiempo de vida
 * del access token en segundos, el tipo de token (siempre {@code "Bearer"}) y
 * la información pública del usuario autenticado.
 *
 * @param tokenAcceso   JWT de corta duración para autorizar peticiones.
 * @param tokenRefresh  token opaco de larga duración para renovar el access token.
 * @param expiraEn      tiempo de vida del access token en segundos.
 * @param tipoToken     tipo de token; siempre {@code "Bearer"}.
 * @param usuario       datos públicos del usuario autenticado.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record RespuestaLogin(
        @JsonProperty("accessToken")
        String       tokenAcceso,

        @JsonProperty("refreshToken")
        String       tokenRefresh,

        @JsonProperty("expiresIn")
        long         expiraEn,

        @JsonProperty("tokenType")
        String       tipoToken,

        @JsonProperty("user")
        RespuestaUsuario usuario
) {
    /**
     * Crea una instancia de {@code RespuestaLogin} con tipo de token predeterminado
     * ({@code "Bearer"}).
     *
     * @param tokenAcceso  JWT de acceso emitido.
     * @param tokenRefresh token de renovación emitido.
     * @param expiraEn     tiempo de vida del access token en segundos.
     * @param usuario      datos públicos del usuario autenticado.
     * @return nueva instancia de {@code RespuestaLogin}.
     */
    public static RespuestaLogin de(String tokenAcceso, String tokenRefresh,
                                    long expiraEn, RespuestaUsuario usuario) {
        return new RespuestaLogin(tokenAcceso, tokenRefresh, expiraEn, "Bearer", usuario);
    }
}
