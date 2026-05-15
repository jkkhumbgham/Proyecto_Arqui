package com.puj.security.rbac;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AuthenticatedUser {

    private String userId;
    private String email;
    private Role   role;
    private String rawToken;
    private boolean authenticated;

    public boolean hasRole(Role... required) {
        if (!authenticated || role == null) return false;
        for (Role r : required) {
            if (role == r) return true;
        }
        return false;
    }

    public boolean isAuthenticated() { return authenticated; }

    public void populate(String userId, String email, Role role, String rawToken) {
        this.userId        = userId;
        this.email         = email;
        this.role          = role;
        this.rawToken      = rawToken;
        this.authenticated = true;
    }

    public String getUserId()   { return userId; }
    public String getEmail()    { return email; }
    public Role   getRole()     { return role; }
    public String getRawToken() { return rawToken; }
}
