package com.puj.ui.bean;

import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.http.HttpResponse;
import java.util.Map;

@Named
@RequestScoped
public class LoginBean {

    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    private String  email;
    private String  password;
    private String  firstName;
    private String  lastName;
    private boolean consentGiven;

    public String login() {
        try {
            String body = api.toJson(Map.of("email", email, "password", password));
            HttpResponse<String> response = api.postPublic(USER_URL + "/api/v1/auth/login", body);

            if (response.statusCode() == 200) {
                var json = api.readTree(response.body());
                session.init(
                        json.get("accessToken").asText(),
                        json.path("user").path("id").asText(),
                        json.path("user").path("role").asText(),
                        json.path("user").path("email").asText()
                );
                password = null;
                return "/views/dashboard?faces-redirect=true";
            }
            FacesMessageUtil.error("Credenciales inválidas.");
        } catch (Exception e) {
            FacesMessageUtil.error("Error de conexión. Intenta de nuevo.");
        }
        return null;
    }

    public String register() {
        try {
            Map<String, Object> bodyMap = Map.of(
                    "email",        email,
                    "password",     password,
                    "firstName",    firstName  != null ? firstName  : "",
                    "lastName",     lastName   != null ? lastName   : "",
                    "consentGiven", consentGiven
            );
            HttpResponse<String> response = api.postPublic(USER_URL + "/api/v1/auth/register",
                    api.toJson(bodyMap));
            if (response.statusCode() == 201) {
                FacesMessageUtil.info("Registro exitoso. Por favor inicia sesión.");
                return "/views/login?faces-redirect=true";
            }
            String msg = api.readTree(response.body()).path("message").asText("Error al registrarse.");
            FacesMessageUtil.error(msg);
        } catch (Exception e) {
            FacesMessageUtil.error("Error de conexión.");
        }
        return null;
    }

    public String redirectIfAuthenticated() {
        if (session.isAuthenticated()) return "/views/dashboard?faces-redirect=true";
        return null;
    }

    public String  getEmail()                  { return email; }
    public void    setEmail(String e)          { this.email = e; }
    public String  getPassword()               { return password; }
    public void    setPassword(String p)       { this.password = p; }
    public String  getFirstName()              { return firstName; }
    public void    setFirstName(String f)      { this.firstName = f; }
    public String  getLastName()               { return lastName; }
    public void    setLastName(String l)       { this.lastName = l; }
    public boolean isConsentGiven()            { return consentGiven; }
    public void    setConsentGiven(boolean c)  { this.consentGiven = c; }
}
