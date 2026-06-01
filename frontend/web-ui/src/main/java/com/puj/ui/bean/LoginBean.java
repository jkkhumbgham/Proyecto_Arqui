package com.puj.ui.bean;

import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Bean JSF de autenticación utilizado por las páginas {@code login.xhtml} y
 * {@code register.xhtml}.
 *
 * <p>Es {@code @RequestScoped}: se crea en cada petición HTTP. Gestiona el
 * inicio de sesión y el registro de nuevos usuarios comunicándose con el
 * microservicio {@code user-service} a través de {@link ApiClientService}.
 * Tras un login exitoso inicializa {@link SessionBean} con el token JWT y
 * los datos del usuario.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Named
@RequestScoped
public class LoginBean {

    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    /** Campo email del formulario de login/registro. */
    private String  email;
    /** Campo contraseña del formulario de login/registro. */
    private String  password;
    /** Campo nombre del formulario de registro. */
    private String  firstName;
    /** Campo apellido del formulario de registro. */
    private String  lastName;
    /** Casilla de consentimiento de datos del formulario de registro. */
    private boolean consentGiven;

    /**
     * Acción JSF de inicio de sesión.
     *
     * <p>Envía las credenciales al endpoint {@code POST /api/v1/auth/login} del
     * servicio de usuarios. Si la respuesta es HTTP 200 inicializa la sesión con
     * el token JWT y redirige al dashboard; de lo contrario muestra un mensaje
     * de error.
     *
     * @return ruta {@code /views/dashboard?faces-redirect=true} en caso de éxito,
     *         {@code null} si las credenciales son inválidas o hay error de conexión
     */
    public String login() {
        try {
            String body = api.toJson(Map.of("email", email, "password", password));
            HttpResponse<String> response =
                    api.postPublic(USER_URL + "/api/v1/auth/login", body);

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

    /**
     * Acción JSF de registro de nuevo usuario.
     *
     * <p>Envía los datos del formulario al endpoint {@code POST /api/v1/auth/register}
     * del servicio de usuarios. Si la respuesta es HTTP 201 redirige al login con
     * un mensaje de éxito; de lo contrario muestra el mensaje de error devuelto
     * por la API.
     *
     * @return ruta {@code /views/login?faces-redirect=true} si el registro fue
     *         exitoso, {@code null} si hubo error
     */
    public String register() {
        try {
            Map<String, Object> bodyMap = Map.of(
                    "email",        email,
                    "password",     password,
                    "firstName",    firstName  != null ? firstName  : "",
                    "lastName",     lastName   != null ? lastName   : "",
                    "consentGiven", consentGiven
            );
            HttpResponse<String> response = api.postPublic(
                    USER_URL + "/api/v1/auth/register", api.toJson(bodyMap));
            if (response.statusCode() == 201) {
                FacesMessageUtil.info("Registro exitoso. Por favor inicia sesión.");
                return "/views/login?faces-redirect=true";
            }
            String msg = api.readTree(response.body())
                    .path("message").asText("Error al registrarse.");
            FacesMessageUtil.error(msg);
        } catch (Exception e) {
            FacesMessageUtil.error("Error de conexión.");
        }
        return null;
    }

    /**
     * Redirige al dashboard si ya hay una sesión activa.
     *
     * <p>Se invoca desde el {@code preRenderView} de {@code login.xhtml} para
     * evitar que usuarios autenticados accedan de nuevo a la página de login.
     *
     * @return ruta {@code /views/dashboard?faces-redirect=true} si el usuario ya
     *         está autenticado, {@code null} en caso contrario
     */
    public String redirectIfAuthenticated() {
        if (session.isAuthenticated()) return "/views/dashboard?faces-redirect=true";
        return null;
    }

    /** Retorna el email del campo de formulario. */
    public String  getEmail()                  { return email; }
    /** Establece el email del campo de formulario. */
    public void    setEmail(String e)          { this.email = e; }
    /** Retorna la contraseña del campo de formulario. */
    public String  getPassword()               { return password; }
    /** Establece la contraseña del campo de formulario. */
    public void    setPassword(String p)       { this.password = p; }
    /** Retorna el nombre del campo de formulario de registro. */
    public String  getFirstName()              { return firstName; }
    /** Establece el nombre del campo de formulario de registro. */
    public void    setFirstName(String f)      { this.firstName = f; }
    /** Retorna el apellido del campo de formulario de registro. */
    public String  getLastName()               { return lastName; }
    /** Establece el apellido del campo de formulario de registro. */
    public void    setLastName(String l)       { this.lastName = l; }
    /** Indica si el usuario marcó la casilla de consentimiento. */
    public boolean isConsentGiven()            { return consentGiven; }
    /** Establece el valor de la casilla de consentimiento. */
    public void    setConsentGiven(boolean c)  { this.consentGiven = c; }
}
