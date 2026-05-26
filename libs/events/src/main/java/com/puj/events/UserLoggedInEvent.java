package com.puj.events;

public class UserLoggedInEvent extends BaseEvent {

    private String userId;
    private String email;
    private String role;

    public UserLoggedInEvent() { super(); }

    public UserLoggedInEvent(String userId, String email, String role) {
        super("USER_LOGGED_IN", "user-service");
        this.userId = userId;
        this.email  = email;
        this.role   = role;
    }

    public String getUserId() { return userId; }
    public String getEmail()  { return email; }
    public String getRole()   { return role; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setEmail(String email)   { this.email  = email; }
    public void setRole(String role)     { this.role   = role; }
}
