package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Named
@ViewScoped
public class ForumBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");
    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String COLLAB_URL =
            System.getenv().getOrDefault("COLLABORATION_SERVICE_URL", "http://collaboration-service:8080");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public static class CourseOption implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, title;
        public CourseOption(String id, String title) { this.id = id; this.title = title; }
        public String getId()    { return id; }
        public String getTitle() { return title; }
    }

    @Inject private SessionBean session;

    private transient HttpClient   http;
    private transient ObjectMapper mapper;

    private HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return http;
    }
    private ObjectMapper mapper() {
        if (mapper == null) mapper = new ObjectMapper();
        return mapper;
    }

    private List<Map<String, Object>> forums  = new ArrayList<>();
    private List<Map<String, Object>> threads = new ArrayList<>();
    private List<Map<String, Object>> posts   = new ArrayList<>();

    private List<CourseOption>  courseOptions   = new ArrayList<>();
    private Map<String, String> courseTitleToId = new LinkedHashMap<>();
    private Map<String, String> authorNameCache = new HashMap<>();

    private String  selectedForumId;
    private String  selectedThreadId;
    private boolean selectedThreadLocked;
    private String  newThreadTitle;
    private String  newThreadContent;
    private String  newPostContent;
    private String  newForumTitle;
    private String  courseSearch;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        loadForums();
        if (session.hasRole("INSTRUCTOR", "ADMIN")) {
            loadCourseOptions();
        }
    }

    private void loadCourseOptions() {
        try {
            String url = session.hasRole("ADMIN", "DIRECTOR")
                    ? COURSE_URL + "/api/v1/courses"
                    : COURSE_URL + "/api/v1/courses/my";
            HttpResponse<String> resp = http().send(bearer(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper().readTree(resp.body());
                JsonNode arr  = root.isArray() ? root : root.path("data");
                arr.forEach(n -> {
                    String id    = n.path("id").asText();
                    String title = n.path("title").asText();
                    if (!id.isBlank() && !title.isBlank()) {
                        courseOptions.add(new CourseOption(id, title));
                        courseTitleToId.put(title, id);
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void loadForums() {
        try {
            HttpResponse<String> resp = http().send(bearer(COLLAB_URL + "/api/v1/forums"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper().readTree(resp.body());
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
            HttpResponse<String> resp = http().send(
                    bearer(COLLAB_URL + "/api/v1/forums/" + selectedForumId + "/threads"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                threads.clear();
                JsonNode arr = mapper().readTree(resp.body());
                arr.forEach(n -> {
                    Map<String, Object> t = new HashMap<>();
                    t.put("id",        n.path("id").asText());
                    t.put("title",     n.path("title").asText());
                    t.put("author",    resolveAuthorName(n.path("authorId").asText()));
                    t.put("postCount", n.path("postCount").asInt());
                    t.put("locked",    n.path("locked").asBoolean(false));
                    t.put("pinned",    n.path("pinned").asBoolean(false));
                    t.put("createdAt", formatDate(n.path("createdAt").asText()));
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
            HttpResponse<String> resp = http().send(
                    bearer(COLLAB_URL + "/api/v1/forums/threads/" + selectedThreadId + "/posts"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                posts.clear();
                JsonNode root = mapper().readTree(resp.body());
                selectedThreadLocked = root.path("locked").asBoolean(false);
                root.path("posts").forEach(n -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id",        n.path("id").asText());
                    p.put("content",   n.path("content").asText());
                    p.put("author",    resolveAuthorName(n.path("authorId").asText()));
                    p.put("createdAt", formatDate(n.path("createdAt").asText()));
                    posts.add(p);
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar los mensajes.");
        }
    }

    private String resolveAuthorName(String userId) {
        if (userId == null || userId.isBlank()) return "Desconocido";
        return authorNameCache.computeIfAbsent(userId, id -> {
            try {
                HttpResponse<String> resp = http().send(bearer(USER_URL + "/api/v1/users/" + id + "/name"),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return mapper().readTree(resp.body()).path("displayName").asText(id);
                }
            } catch (Exception ignored) {}
            return id;
        });
    }

    private String formatDate(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            return DATE_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    public String deleteThread(String threadId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COLLAB_URL + "/api/v1/forums/threads/" + threadId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .DELETE().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 204) {
                loadThreads();
            } else {
                warn("No se pudo eliminar el hilo.");
            }
        } catch (Exception e) {
            warn("Error al eliminar el hilo.");
        }
        return null;
    }

    public String lockThread(String threadId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COLLAB_URL + "/api/v1/forums/threads/" + threadId + "/lock"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                loadThreads();
            } else {
                warn("No se pudo bloquear el hilo.");
            }
        } catch (Exception e) {
            warn("Error al bloquear el hilo.");
        }
        return null;
    }

    public String createForum() {
        String resolvedId = courseSearch != null ? courseTitleToId.get(courseSearch.trim()) : null;
        if (resolvedId == null) {
            warn("Selecciona un curso válido de la lista de sugerencias.");
            return null;
        }
        try {
            String body = mapper().writeValueAsString(Map.of(
                    "title",    newForumTitle != null ? newForumTitle : "",
                    "courseId", resolvedId));
            HttpResponse<String> resp = http().send(post(COLLAB_URL + "/api/v1/forums", body),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) return "forums?faces-redirect=true";
            warn("No se pudo crear el foro.");
        } catch (Exception e) {
            warn("Error al crear el foro.");
        }
        return null;
    }

    public String createThread() {
        if (selectedForumId == null) { warn("Selecciona un foro primero."); return null; }
        if (newThreadTitle == null || newThreadTitle.isBlank()) { warn("El título del hilo es obligatorio."); return null; }
        if (newThreadContent == null || newThreadContent.isBlank()) { warn("El contenido del hilo es obligatorio."); return null; }
        try {
            String body = mapper().writeValueAsString(Map.of(
                    "title",   newThreadTitle.trim(),
                    "content", newThreadContent.trim()));
            HttpResponse<String> resp = http().send(
                    post(COLLAB_URL + "/api/v1/forums/" + selectedForumId + "/threads", body),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                newThreadTitle   = null;
                newThreadContent = null;
                loadThreads();
                return null;
            }
            warn("No se pudo crear el hilo (" + resp.statusCode() + ").");
        } catch (Exception e) {
            warn("Error al crear el hilo.");
        }
        return null;
    }

    public String replyThread() {
        if (selectedThreadId == null) return null;
        if (newPostContent == null || newPostContent.isBlank()) { warn("Escribe un mensaje antes de publicar."); return null; }
        try {
            String body = mapper().writeValueAsString(Map.of("content", newPostContent.trim()));
            HttpResponse<String> resp = http().send(
                    post(COLLAB_URL + "/api/v1/forums/threads/" + selectedThreadId + "/posts", body),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                newPostContent = null;
                loadPosts();
                return null;
            }
            warn("No se pudo publicar la respuesta (" + resp.statusCode() + ").");
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
    public List<Map<String, Object>> getForums()           { return forums; }
    public List<Map<String, Object>> getThreads()          { return threads; }
    public List<Map<String, Object>> getPosts()            { return posts; }
    public List<CourseOption>        getCourseOptions()    { return courseOptions; }
    public boolean isSelectedThreadLocked()                { return selectedThreadLocked; }
    public String  getSelectedForumId()                    { return selectedForumId; }
    public void    setSelectedForumId(String id)           { this.selectedForumId = id; }
    public String  getSelectedThreadId()                   { return selectedThreadId; }
    public void    setSelectedThreadId(String id)          { this.selectedThreadId = id; }
    public void    setSelectedThreadLocked(boolean v)      { this.selectedThreadLocked = v; }
    public String  getNewThreadTitle()                     { return newThreadTitle; }
    public void    setNewThreadTitle(String t)             { this.newThreadTitle = t; }
    public String  getNewThreadContent()                   { return newThreadContent; }
    public void    setNewThreadContent(String c)           { this.newThreadContent = c; }
    public String  getNewPostContent()                     { return newPostContent; }
    public void    setNewPostContent(String c)             { this.newPostContent = c; }
    public String  getNewForumTitle()                      { return newForumTitle; }
    public void    setNewForumTitle(String t)              { this.newForumTitle = t; }
    public String  getCourseSearch()                       { return courseSearch; }
    public void    setCourseSearch(String s)               { this.courseSearch = s; }
}
