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
 * Singleton HTTP + JSON client compartido por todos los beans JSF.
 * Elimina la duplicación de HttpClient, ObjectMapper y el método bearer()
 * que existía en cada bean por separado.
 */
@ApplicationScoped
public class ApiClientService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient   http   = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpResponse<String> get(String url, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET().timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> getWithOptionalAuth(String url, String token) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(TIMEOUT);
        if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
        return http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> getPublic(String url) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET().timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String url, String body, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String url, String body, String token, Duration timeout) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postEmpty(String url, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postPublic(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> put(String url, String body, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> put(String url, String body, String token, Duration timeout) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> delete(String url, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .DELETE().timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public JsonNode readTree(String json) throws Exception {
        return mapper.readTree(json);
    }

    public String toJson(Object obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }

    public ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    public ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }

    public String errorMessage(HttpResponse<String> resp, String fallback) {
        try { return mapper.readTree(resp.body()).path("message").asText(fallback); }
        catch (Exception e) { return fallback; }
    }
}
