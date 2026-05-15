package com.puj.events;

public class UserRegisteredEvent extends BaseEvent {

    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;

    public UserRegisteredEvent() { super(); }

    public UserRegisteredEvent(String userId, String email, String firstName,
                               String lastName, String role) {
        super("USER_REGISTERED", "user-service");
        this.userId    = userId;
        this.email     = email;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.role      = role;
    }

    public String getUserId()    { return userId; }
    public String getEmail()     { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName()  { return lastName; }
    public String getRole()      { return role; }

    public void setUserId(String userId)       { this.userId = userId; }
    public void setEmail(String email)         { this.email = email; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName)   { this.lastName = lastName; }
    public void setRole(String role)           { this.role = role; }
}
