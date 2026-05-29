package com.puj.ui.util;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

public final class FacesMessageUtil {

    private FacesMessageUtil() {}

    public static void warn(String msg) {
        add(FacesMessage.SEVERITY_WARN, msg);
    }

    public static void info(String msg) {
        add(FacesMessage.SEVERITY_INFO, msg);
    }

    public static void error(String msg) {
        add(FacesMessage.SEVERITY_ERROR, msg);
    }

    private static void add(FacesMessage.Severity severity, String msg) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, msg, null));
    }
}
