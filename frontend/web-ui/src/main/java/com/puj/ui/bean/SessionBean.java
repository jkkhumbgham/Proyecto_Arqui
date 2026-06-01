package com.puj.ui.bean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

import java.io.Serializable;

/**
 * Bean JSF de sesión que centraliza el estado de autenticación del usuario.
 *
 * <p>Es {@code @SessionScoped}: existe una instancia por sesión HTTP y persiste
 * entre peticiones. Todos los demás beans lo inyectan para consultar el token
 * de acceso JWT, el rol y el identificador del usuario autenticado.
 *
 * <p>Se usa desde prácticamente todas las páginas XHTML de la aplicación para
 * decidir qué elementos de navegación mostrar y para proteger operaciones.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Named
@SessionScoped
public class SessionBean implements Serializable {

    private String accessToken;
    private String userId;
    private String userRole;
    private String userEmail;

    /**
     * Inicializa la sesión tras un inicio de sesión exitoso.
     *
     * <p>Guarda el token JWT y los datos básicos del usuario para que estén
     * disponibles en las peticiones posteriores.
     *
     * @param accessToken token JWT de acceso retornado por el servicio de usuarios
     * @param userId      identificador único del usuario autenticado
     * @param userRole    rol del usuario (p. ej. STUDENT, INSTRUCTOR, ADMIN)
     * @param userEmail   correo electrónico del usuario autenticado
     */
    public void init(String accessToken, String userId,
                     String userRole, String userEmail) {
        this.accessToken = accessToken;
        this.userId      = userId;
        this.userRole    = userRole;
        this.userEmail   = userEmail;
    }

    /**
     * Cierra la sesión invalidando la sesión HTTP y redirige al login.
     *
     * @return navegación JSF hacia {@code /views/login} con redirect
     */
    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/views/login?faces-redirect=true";
    }

    /**
     * Indica si hay un usuario autenticado en la sesión actual.
     *
     * @return {@code true} si el token de acceso no es nulo ni vacío
     */
    public boolean isAuthenticated() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * Comprueba si el usuario autenticado posee alguno de los roles indicados.
     *
     * <p>La comparación ignora mayúsculas y minúsculas.
     *
     * @param roles uno o más roles a verificar
     * @return {@code true} si el rol del usuario coincide con alguno de los
     *         valores suministrados
     */
    public boolean hasRole(String... roles) {
        if (userRole == null) return false;
        for (String r : roles) {
            if (userRole.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    /**
     * Redirige al login si el usuario no está autenticado.
     *
     * <p>Se llama desde los {@code preRenderView} de páginas protegidas para
     * evitar acceso anónimo.
     *
     * @return navegación hacia {@code /views/login} si no está autenticado,
     *         {@code null} si la sesión es válida
     */
    public String guardPage() {
        if (!isAuthenticated()) {
            return "/views/login?faces-redirect=true";
        }
        return null;
    }

    /** Retorna el token JWT de acceso de la sesión actual. */
    public String getAccessToken() { return accessToken; }

    /** Retorna el identificador único del usuario autenticado. */
    public String getUserId()      { return userId; }

    /** Retorna el rol del usuario autenticado (p. ej. STUDENT, INSTRUCTOR). */
    public String getUserRole()    { return userRole; }

    /** Retorna el correo electrónico del usuario autenticado. */
    public String getUserEmail()   { return userEmail; }
}
