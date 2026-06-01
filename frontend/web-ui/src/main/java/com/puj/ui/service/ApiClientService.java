package com.puj.ui.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Singleton HTTP + JSON compartido por todos los beans JSF de la aplicación.
 *
 * <p>Centraliza el {@link HttpClient}, el {@link ObjectMapper} y los métodos
 * de construcción de peticiones con o sin token Bearer, evitando la duplicación
 * que existía en cada bean. El tiempo de espera predeterminado para conexión y
 * lectura es de 5 segundos.
 *
 * <p>Esta clase es {@code @ApplicationScoped}: existe una única instancia
 * durante toda la vida de la aplicación.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class ApiClientService {

    /** Tiempo máximo de espera para conexión y lectura de respuesta. */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient   http   = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Realiza una petición GET autenticada con token Bearer.
     *
     * @param url   URL absoluta del recurso
     * @param token token JWT de acceso
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> get(String url, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET().timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición GET añadiendo el token Bearer solo si no es nulo ni vacío.
     *
     * @param url   URL absoluta del recurso
     * @param token token JWT de acceso, puede ser {@code null}
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> getWithOptionalAuth(String url, String token) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(TIMEOUT);
        if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
        return http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición GET sin autenticación (endpoint público).
     *
     * @param url URL absoluta del recurso
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> getPublic(String url) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET().timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición POST autenticada con cuerpo JSON y timeout predeterminado.
     *
     * @param url   URL absoluta del recurso
     * @param body  cuerpo de la petición serializado como JSON
     * @param token token JWT de acceso
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> post(String url, String body, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición POST autenticada con cuerpo JSON y timeout personalizado.
     *
     * @param url     URL absoluta del recurso
     * @param body    cuerpo de la petición serializado como JSON
     * @param token   token JWT de acceso
     * @param timeout duración máxima de espera para esta petición
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> post(String url, String body, String token,
                                     Duration timeout) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición POST autenticada sin cuerpo (body vacío).
     *
     * <p>Usada principalmente para acciones de tipo "toggle" o "finalize" que
     * no requieren payload.
     *
     * @param url   URL absoluta del recurso
     * @param token token JWT de acceso
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> postEmpty(String url, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición POST pública (sin autenticación) con cuerpo JSON.
     *
     * <p>Se usa para endpoints de registro e inicio de sesión.
     *
     * @param url  URL absoluta del recurso
     * @param body cuerpo de la petición serializado como JSON
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> postPublic(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición PUT autenticada con cuerpo JSON y timeout predeterminado.
     *
     * @param url   URL absoluta del recurso
     * @param body  cuerpo de la petición serializado como JSON
     * @param token token JWT de acceso
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> put(String url, String body, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición PUT autenticada con cuerpo JSON y timeout personalizado.
     *
     * @param url     URL absoluta del recurso
     * @param body    cuerpo de la petición serializado como JSON
     * @param token   token JWT de acceso
     * @param timeout duración máxima de espera para esta petición
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> put(String url, String body, String token,
                                    Duration timeout) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Realiza una petición DELETE autenticada.
     *
     * @param url   URL absoluta del recurso a eliminar
     * @param token token JWT de acceso
     * @return respuesta HTTP con cuerpo en texto plano
     * @throws Exception si ocurre un error de red o de E/S
     */
    public HttpResponse<String> delete(String url, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .DELETE().timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Deserializa una cadena JSON en un {@link JsonNode}.
     *
     * @param json cadena JSON a parsear
     * @return árbol de nodos Jackson
     * @throws Exception si el JSON es inválido
     */
    public JsonNode readTree(String json) throws Exception {
        return mapper.readTree(json);
    }

    /**
     * Serializa un objeto Java en su representación JSON.
     *
     * @param obj objeto a serializar
     * @return cadena JSON
     * @throws JsonProcessingException si el objeto no puede serializarse
     */
    public String toJson(Object obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }

    /**
     * Crea un nuevo {@link ObjectNode} vacío listo para construir JSON.
     *
     * @return nodo objeto Jackson vacío
     */
    public ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    /**
     * Crea un nuevo {@link ArrayNode} vacío listo para construir arrays JSON.
     *
     * @return nodo array Jackson vacío
     */
    public ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }

    /**
     * Extrae el campo {@code message} del cuerpo de una respuesta HTTP de error.
     *
     * <p>Si el cuerpo no contiene dicho campo o no puede parsearse, retorna
     * el valor {@code fallback} indicado.
     *
     * @param resp     respuesta HTTP cuyo cuerpo se inspeccionará
     * @param fallback mensaje a retornar si no se puede extraer {@code message}
     * @return mensaje de error localizado o el valor de {@code fallback}
     */
    public String errorMessage(HttpResponse<String> resp, String fallback) {
        try {
            return mapper.readTree(resp.body()).path("message").asText(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
