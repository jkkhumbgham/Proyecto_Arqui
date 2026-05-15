package com.puj.ui.bean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

import java.io.Serializable;

/**
 * Centralises session state and logout so other beans can depend on it.
 */
@Named
@SessionScoped
public class SessionBean implements Serializable {

    private String accessToken;
    private String userId;
    private String userRole;
    private String userEmail;

    public void init(String accessToken, String userId, String userRole, String userEmail) {
        this.accessToken = accessToken;
        this.userId      = userId;
        this.userRole    = userRole;
        this.userEmail   = userEmail;
    }

    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/views/login?faces-redirect=true";
    }

    public boolean isAuthenticated() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean hasRole(String... roles) {
        if (userRole == null) return false;
        for (String r : roles) {
            if (userRole.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    public String guardPage() {
        if (!isAuthenticated()) {
            return "/views/login?faces-redirect=true";
        }
        return null;
    }

    // ---- accessors ----
    public String getAccessToken() { return accessToken; }
    public String getUserId()      { return userId; }
    public String getUserRole()    { return userRole; }
    public String getUserEmail()   { return userEmail; }
}
