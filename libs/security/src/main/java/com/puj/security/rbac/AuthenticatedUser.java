package com.puj.security.rbac;

import jakarta.enterprise.context.RequestScoped;

/**
 * Contexto del usuario autenticado para el scope de una solicitud HTTP.
 *
 * <p>Es poblado por {@link com.puj.security.jwt.JwtFilter} tras validar el JWT.
 * Si la solicitud no incluye un Bearer token válido, el bean permanece en estado
 * no autenticado ({@link #isAuthenticated()} retorna {@code false}).</p>
 *
 * <p>Todos los beans CDI que necesiten conocer el usuario actual deben inyectar
 * este bean en vez de parsear el JWT directamente.</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see com.puj.security.jwt.JwtFilter
 * @see RbacInterceptor
 */
@RequestScoped
public class AuthenticatedUser {

    private String  userId;
    private String  email;
    private Role    role;
    private String  rawToken;
    private boolean authenticated;

    /**
     * Puebla el contexto con los datos del usuario autenticado.
     *
     * <p>Solo debe ser invocado por {@link com.puj.security.jwt.JwtFilter}
     * tras validar el JWT con éxito.</p>
     *
     * @param userId   identificador único del usuario (UUID como String)
     * @param email    correo institucional del usuario
     * @param role     rol asignado al usuario
     * @param rawToken token JWT original para reenvío a otros servicios
     */
    public void populate(String userId, String email, Role role, String rawToken) {
        this.userId        = userId;
        this.email         = email;
        this.role          = role;
        this.rawToken      = rawToken;
        this.authenticated = true;
    }

    /**
     * Verifica si el usuario tiene al menos uno de los roles indicados.
     *
     * @param required uno o más roles a comprobar
     * @return {@code true} si el usuario está autenticado y su rol está en la lista
     */
    public boolean hasRole(Role... required) {
        if (!authenticated || role == null) return false;
        for (Role r : required) {
            if (role == r) return true;
        }
        return false;
    }

    /**
     * Indica si la solicitud proviene de un usuario autenticado.
     *
     * @return {@code true} si el JWT fue validado con éxito para esta solicitud
     */
    public boolean isAuthenticated() { return authenticated; }

    /**
     * Retorna el identificador único del usuario.
     *
     * @return UUID del usuario como String; {@code null} si no está autenticado
     */
    public String getUserId()   { return userId; }

    /**
     * Retorna el correo institucional del usuario.
     *
     * @return correo del usuario; {@code null} si no está autenticado
     */
    public String getEmail()    { return email; }

    /**
     * Retorna el rol asignado al usuario.
     *
     * @return rol del usuario; {@code null} si no está autenticado
     */
    public Role getRole()       { return role; }

    /**
     * Retorna el token JWT crudo para reenvío en llamadas inter-servicios.
     *
     * @return Bearer token; {@code null} si no está autenticado
     */
    public String getRawToken() { return rawToken; }
}
