package com.puj.seguridad.rbac;

import jakarta.enterprise.context.RequestScoped;

/**
 * Contexto del usuario autenticado para el scope de una solicitud HTTP.
 *
 * <p>Es poblado por {@link com.puj.seguridad.jwt.FiltroJwt} tras validar el JWT.
 * Si la solicitud no incluye un Bearer token válido, el bean permanece en estado
 * no autenticado ({@link #estaAutenticado()} retorna {@code false}).</p>
 *
 * <p>Todos los beans CDI que necesiten conocer el usuario actual deben inyectar
 * este bean en vez de parsear el JWT directamente.</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see com.puj.seguridad.jwt.FiltroJwt
 * @see InterceptorRbac
 */
@RequestScoped
public class UsuarioAutenticado {

    private String  idUsuario;
    private String  correo;
    private Rol     rol;
    private String  tokenBruto;
    private boolean autenticado;

    /**
     * Puebla el contexto con los datos del usuario autenticado.
     *
     * <p>Solo debe ser invocado por {@link com.puj.seguridad.jwt.FiltroJwt}
     * tras validar el JWT con éxito.</p>
     *
     * @param idUsuario  identificador único del usuario (UUID como String)
     * @param correo     correo institucional del usuario
     * @param rol        rol asignado al usuario
     * @param tokenBruto token JWT original para reenvío a otros servicios
     */
    public void poblar(String idUsuario, String correo, Rol rol, String tokenBruto) {
        this.idUsuario  = idUsuario;
        this.correo     = correo;
        this.rol        = rol;
        this.tokenBruto = tokenBruto;
        this.autenticado = true;
    }

    /**
     * Verifica si el usuario tiene al menos uno de los roles indicados.
     *
     * @param requeridos uno o más roles a comprobar
     * @return {@code true} si el usuario está autenticado y su rol está en la lista
     */
    public boolean tieneRol(Rol... requeridos) {
        if (!autenticado || rol == null) return false;
        for (Rol r : requeridos) {
            if (rol == r) return true;
        }
        return false;
    }

    /**
     * Indica si la solicitud proviene de un usuario autenticado.
     *
     * @return {@code true} si el JWT fue validado con éxito para esta solicitud
     */
    public boolean estaAutenticado() { return autenticado; }

    /**
     * Retorna el identificador único del usuario.
     *
     * @return UUID del usuario como String; {@code null} si no está autenticado
     */
    public String obtenerIdUsuario()  { return idUsuario; }

    /**
     * Retorna el correo institucional del usuario.
     *
     * @return correo del usuario; {@code null} si no está autenticado
     */
    public String obtenerCorreo()     { return correo; }

    /**
     * Retorna el rol asignado al usuario.
     *
     * @return rol del usuario; {@code null} si no está autenticado
     */
    public Rol obtenerRol()           { return rol; }

    /**
     * Retorna el token JWT crudo para reenvío en llamadas inter-servicios.
     *
     * @return Bearer token; {@code null} si no está autenticado
     */
    public String obtenerTokenBruto() { return tokenBruto; }
}
