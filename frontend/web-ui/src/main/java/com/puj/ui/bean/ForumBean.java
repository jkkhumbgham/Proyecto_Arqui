package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@RequestScoped
public class ForumBean {

    private static final String COLLAB_URL =
            System.getenv().getOrDefault("COLLABORATION_SERVICE_URL", "http://collaboration-service:8080");

    @Inject private SessionBean session;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private List<Map<String, Object>> forums  = new ArrayList<>();
    private List<Map<String, Object>> threads = new ArrayList<>();
    private List<Map<String, Object>> posts   = new ArrayList<>();

    private String selectedForumId;
    private String selectedThreadId;
    private String newThreadTitle;
    private String newPostContent;
    private String newForumTitle;
    private String newForumCourseId;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        loadForums();
    }

    private void loadForums() {
        try {
            HttpRequest req = bearer(COLLAB_URL + "/api/v1/forums");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> forums.add(Map.of(
                        "id",       n.path("id").asText(),
                        "title",    n.path("title").asText(),
                        "courseId", n.path("courseId").asText()
                )));
            }
        } catch (Exception e) {
            warn("No se pudo cargar los foros.");
        }
    }

    public void loadThreads() {
        if (selectedForumId == null) return;
        try {
            HttpRequest req = bearer(COLLAB_URL + "/api/v1/forums/" + selectedForumId + "/threads");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                threads.clear();
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> {
                    Map<String, Object> t = new HashMap<>();
                    t.put("id",        n.path("id").asText());
                    t.put("title",     n.path("title").asText());
                    t.put("author",    n.path("authorEmail").asText());
                    t.put("postCount", n.path("postCount").asInt());
                    threads.add(t);
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar los hilos.");
        }
    }

    public void loadPosts() {
        if (selectedThreadId == null) return;
        try {
            HttpRequest req = bearer(COLLAB_URL + "/api/v1/forums/threads/" + selectedThreadId + "/posts");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                posts.clear();
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id",        n.path("id").asText());
                    p.put("content",   n.path("content").asText());
                    p.put("author",    n.path("authorEmail").asText());
                    p.put("createdAt", n.path("createdAt").asText());
                    posts.add(p);
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar los mensajes.");
        }
    }

    public String createForum() {
        try {
            String body = String.format("{\"title\":\"%s\",\"courseId\":\"%s\"}",
                    newForumTitle, newForumCourseId);
            HttpRequest req = post(COLLAB_URL + "/api/v1/forums", body);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                return "forums?faces-redirect=true";
            }
            warn("No se pudo crear el foro.");
        } catch (Exception e) {
            warn("Error al crear el foro.");
        }
        return null;
    }

    public String createThread() {
        if (selectedForumId == null) return null;
        try {
            String body = String.format("{\"title\":\"%s\"}", newThreadTitle);
            HttpRequest req = post(COLLAB_URL + "/api/v1/forums/" + selectedForumId + "/threads", body);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                newThreadTitle = null;
                loadThreads();
                return null;
            }
            warn("No se pudo crear el hilo.");
        } catch (Exception e) {
            warn("Error al crear el hilo.");
        }
        return null;
    }

    public String replyThread() {
        if (selectedThreadId == null) return null;
        try {
            String body = String.format("{\"content\":\"%s\"}", newPostContent);
            HttpRequest req = post(COLLAB_URL + "/api/v1/forums/threads/" + selectedThreadId + "/posts", body);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                newPostContent = null;
                loadPosts();
                return null;
            }
            warn("No se pudo publicar la respuesta.");
        } catch (Exception e) {
            warn("Error al publicar.");
        }
        return null;
    }

    private HttpRequest bearer(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .GET().timeout(Duration.ofSeconds(5)).build();
    }

    private HttpRequest post(String url, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5)).build();
    }

    private void warn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }

    // ---- accessors ----
    public List<Map<String, Object>> getForums()   { return forums; }
    public List<Map<String, Object>> getThreads()  { return threads; }
    public List<Map<String, Object>> getPosts()    { return posts; }
    public String getSelectedForumId()             { return selectedForumId; }
    public void   setSelectedForumId(String id)    { this.selectedForumId = id; }
    public String getSelectedThreadId()            { return selectedThreadId; }
    public void   setSelectedThreadId(String id)   { this.selectedThreadId = id; }
    public String getNewThreadTitle()              { return newThreadTitle; }
    public void   setNewThreadTitle(String t)      { this.newThreadTitle = t; }
    public String getNewPostContent()              { return newPostContent; }
    public void   setNewPostContent(String c)      { this.newPostContent = c; }
    public String getNewForumTitle()               { return newForumTitle; }
    public void   setNewForumTitle(String t)       { this.newForumTitle = t; }
    public String getNewForumCourseId()            { return newForumCourseId; }
    public void   setNewForumCourseId(String id)   { this.newForumCourseId = id; }
}
