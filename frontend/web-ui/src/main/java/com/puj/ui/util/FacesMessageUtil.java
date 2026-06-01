package com.puj.ui.util;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

/**
 * Utilidad estática para agregar mensajes JSF al contexto actual.
 *
 * <p>Centraliza la creación de {@link FacesMessage} con distintos niveles de
 * severidad (INFO, WARN, ERROR) para que todos los beans de la aplicación
 * puedan notificar al usuario sin duplicar código.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
public final class FacesMessageUtil {

    /** Constructor privado — clase de utilidad no instanciable. */
    private FacesMessageUtil() {}

    /**
     * Agrega un mensaje de advertencia al contexto JSF actual.
     *
     * @param msg texto del mensaje que se mostrará al usuario
     */
    public static void warn(String msg) {
        add(FacesMessage.SEVERITY_WARN, msg);
    }

    /**
     * Agrega un mensaje informativo al contexto JSF actual.
     *
     * @param msg texto del mensaje que se mostrará al usuario
     */
    public static void info(String msg) {
        add(FacesMessage.SEVERITY_INFO, msg);
    }

    /**
     * Agrega un mensaje de error al contexto JSF actual.
     *
     * @param msg texto del mensaje que se mostrará al usuario
     */
    public static void error(String msg) {
        add(FacesMessage.SEVERITY_ERROR, msg);
    }

    /**
     * Crea y registra un {@link FacesMessage} con la severidad indicada en el
     * contexto JSF sin asociarlo a ningún componente (clientId = null).
     *
     * @param severity nivel de severidad del mensaje
     * @param msg      texto del mensaje
     */
    private static void add(FacesMessage.Severity severity, String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, msg, null));
    }
}
