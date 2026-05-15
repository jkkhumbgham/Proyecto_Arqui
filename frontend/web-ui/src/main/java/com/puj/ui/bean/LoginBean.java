package com.puj.ui.bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Named
@RequestScoped
public class LoginBean {

    private static final String USER_SERVICE_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");

    @Inject private SessionBean session;

    private String email;
    private String password;
    private boolean consentGiven;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public String login() {
        try {
            String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_SERVICE_URL + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var json = mapper.readTree(response.body());
                session.init(
                        json.get("accessToken").asText(),
                        json.path("user").path("id").asText(),
                        json.path("user").path("role").asText(),
                        json.path("user").path("email").asText()
                );
                password = null;
                return "/views/dashboard?faces-redirect=true";
            } else {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR,
                                "Credenciales inválidas.", null));
                return null;
            }
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Error de conexión. Intenta de nuevo.", null));
            return null;
        }
    }

    public String register() {
        try {
            String body = String.format(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"STUDENT\",\"consentGiven\":%b}",
                    email, password, consentGiven);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_SERVICE_URL + "/api/v1/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO,
                                "Registro exitoso. Por favor inicia sesión.", null));
                return "/views/login?faces-redirect=true";
            }
            var err = mapper.readTree(response.body());
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            err.path("message").asText("Error al registrarse."), null));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Error de conexión.", null));
        }
        return null;
    }

    public String redirectIfAuthenticated() {
        if (session.isAuthenticated()) {
            return "/views/dashboard?faces-redirect=true";
        }
        return null;
    }

    public String getEmail()           { return email; }
    public void   setEmail(String e)   { this.email = e; }
    public String getPassword()        { return password; }
    public void   setPassword(String p){ this.password = p; }
    public boolean isConsentGiven()    { return consentGiven; }
    public void    setConsentGiven(boolean c) { this.consentGiven = c; }
}
