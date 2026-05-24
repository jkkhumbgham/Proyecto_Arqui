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
import java.util.List;
import java.util.Map;

@Named
@ViewScoped
public class StudyGroupBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String COLLAB_URL =
            System.getenv().getOrDefault("COLLABORATION_SERVICE_URL", "http://collaboration-service:8080");
    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

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

    private List<Map<String, Object>> courses  = new ArrayList<>();
    private List<Map<String, Object>> groups   = new ArrayList<>();
    private Map<String, Object>       detail   = null;
    private List<Map<String, Object>> messages = new ArrayList<>();

    private Map<String, String> authorNameCache = new HashMap<>();

    private String selectedCourseId;
    private String selectedGroupId;
    private String newGroupName;
    private int    newGroupMaxMembers = 10;
    private String newMessage;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        loadCourses();
    }

    private void loadCourses() {
        try {
            if (session.hasRole("ADMIN", "DIRECTOR")) {
                HttpResponse<String> resp = http().send(bearer(COURSE_URL + "/api/v1/courses"),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode root = mapper().readTree(resp.body());
                    (root.isArray() ? root : root.path("data")).forEach(n -> addCourse(n));
                }
            } else if (session.hasRole("INSTRUCTOR")) {
                HttpResponse<String> resp = http().send(bearer(COURSE_URL + "/api/v1/courses/my"),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode root = mapper().readTree(resp.body());
                    (root.isArray() ? root : root.path("data")).forEach(n -> addCourse(n));
                }
            } else {
                // STUDENT: load enrolled courses
                HttpResponse<String> enrollResp = http().send(bearer(COURSE_URL + "/api/v1/enrollments/my"),
                        HttpResponse.BodyHandlers.ofString());
                if (enrollResp.statusCode() == 200) {
                    JsonNode arr = mapper().readTree(enrollResp.body());
                    arr.forEach(e -> {
                        String courseId = e.path("courseId").asText();
                        if (!courseId.isBlank()) {
                            try {
                                HttpResponse<String> cr = http().send(bearer(COURSE_URL + "/api/v1/courses/" + courseId),
                                        HttpResponse.BodyHandlers.ofString());
                                if (cr.statusCode() == 200) addCourse(mapper().readTree(cr.body()));
                            } catch (Exception ignored) {}
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private void addCourse(JsonNode n) {
        String id    = n.path("id").asText();
        String title = n.path("title").asText();
        if (!id.isBlank() && !title.isBlank()) {
            Map<String, Object> c = new HashMap<>();
            c.put("id",    id);
            c.put("title", title);
            courses.add(c);
        }
    }

    public void loadGroups() {
        if (selectedCourseId == null || selectedCourseId.isBlank()) return;
        groups.clear();
        detail = null;
        messages.clear();
        selectedGroupId = null;
        try {
            HttpResponse<String> resp = http().send(
                    bearer(COLLAB_URL + "/api/v1/groups/courses/" + selectedCourseId),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                mapper().readTree(resp.body()).forEach(n -> {
                    Map<String, Object> g = new HashMap<>();
                    g.put("id",          n.path("id").asText());
                    g.put("name",        n.path("name").asText());
                    g.put("maxMembers",  n.path("maxMembers").asInt());
                    g.put("memberCount", n.path("memberCount").asInt());
                    g.put("isMember",    n.path("isMember").asBoolean(false));
                    g.put("isTutor",     n.path("isTutor").asBoolean(false));
                    groups.add(g);
                });
            }
        } catch (Exception e) {
            warn("No se pudieron cargar los grupos.");
        }
    }

    public void loadGroupDetail() {
        if (selectedGroupId == null) return;
        try {
            HttpResponse<String> detailResp = http().send(
                    bearer(COLLAB_URL + "/api/v1/groups/" + selectedGroupId),
                    HttpResponse.BodyHandlers.ofString());
            if (detailResp.statusCode() == 200) {
                JsonNode root = mapper().readTree(detailResp.body());
                detail = new HashMap<>();
                detail.put("id",         root.path("id").asText());
                detail.put("name",       root.path("name").asText());
                detail.put("maxMembers", root.path("maxMembers").asInt());
                List<Map<String, Object>> memberList = new ArrayList<>();
                root.path("members").forEach(m -> {
                    Map<String, Object> mem = new HashMap<>();
                    String uid = m.path("userId").asText();
                    mem.put("userId",      uid);
                    mem.put("displayName", resolveAuthorName(uid));
                    mem.put("isTutor",     m.path("isTutor").asBoolean(false));
                    mem.put("joinedAt",    formatDate(m.path("joinedAt").asText()));
                    memberList.add(mem);
                });
                detail.put("members", memberList);
            }

            HttpResponse<String> msgResp = http().send(
                    bearer(COLLAB_URL + "/api/v1/groups/" + selectedGroupId + "/messages"),
                    HttpResponse.BodyHandlers.ofString());
            messages.clear();
            if (msgResp.statusCode() == 200) {
                mapper().readTree(msgResp.body()).forEach(m -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("authorName", resolveAuthorName(m.path("authorId").asText()));
                    msg.put("content",    m.path("content").asText());
                    msg.put("sentAt",     formatDate(m.path("sentAt").asText()));
                    messages.add(msg);
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar el detalle del grupo.");
        }
    }

    public String createGroup() {
        if (selectedCourseId == null || selectedCourseId.isBlank()) {
            warn("Selecciona un curso primero."); return null;
        }
        if (newGroupName == null || newGroupName.isBlank()) {
            warn("El nombre del grupo es obligatorio."); return null;
        }
        try {
            String body = mapper().writeValueAsString(Map.of(
                    "name",       newGroupName.trim(),
                    "courseId",   selectedCourseId,
                    "maxMembers", newGroupMaxMembers > 0 ? newGroupMaxMembers : 10));
            HttpResponse<String> resp = http().send(post(COLLAB_URL + "/api/v1/groups", body),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                newGroupName = null;
                newGroupMaxMembers = 10;
                loadGroups();
            } else {
                warn("No se pudo crear el grupo (" + resp.statusCode() + ").");
            }
        } catch (Exception e) {
            warn("Error al crear el grupo.");
        }
        return null;
    }

    public String joinGroup(String groupId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COLLAB_URL + "/api/v1/groups/" + groupId + "/join"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                loadGroups();
            } else {
                try { warn(mapper().readTree(resp.body()).path("message").asText("No se pudo unir al grupo.")); }
                catch (Exception ex) { warn("No se pudo unir al grupo."); }
            }
        } catch (Exception e) {
            warn("Error al unirse al grupo.");
        }
        return null;
    }

    public String leaveGroup(String groupId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COLLAB_URL + "/api/v1/groups/" + groupId + "/leave"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                if (groupId.equals(selectedGroupId)) {
                    selectedGroupId = null;
                    detail = null;
                    messages.clear();
                }
                loadGroups();
            } else {
                warn("No se pudo salir del grupo.");
            }
        } catch (Exception e) {
            warn("Error al salir del grupo.");
        }
        return null;
    }

    public String deleteGroup(String groupId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COLLAB_URL + "/api/v1/groups/" + groupId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .DELETE().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 204) {
                if (groupId.equals(selectedGroupId)) {
                    selectedGroupId = null;
                    detail = null;
                    messages.clear();
                }
                loadGroups();
            } else {
                warn("No se pudo eliminar el grupo.");
            }
        } catch (Exception e) {
            warn("Error al eliminar el grupo.");
        }
        return null;
    }

    public String sendMessage() {
        if (selectedGroupId == null || newMessage == null || newMessage.isBlank()) return null;
        try {
            String body = mapper().writeValueAsString(Map.of("content", newMessage.trim()));
            HttpResponse<String> resp = http().send(
                    post(COLLAB_URL + "/api/v1/groups/" + selectedGroupId + "/messages", body),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                newMessage = null;
                loadGroupDetail();
            } else {
                warn("No se pudo enviar el mensaje.");
            }
        } catch (Exception e) {
            warn("Error al enviar el mensaje.");
        }
        return null;
    }

    public String resolveAuthorName(String userId) {
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

    public String formatDate(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try { return DATE_FMT.format(Instant.parse(iso)); }
        catch (Exception e) { return iso; }
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

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDetailMembers() {
        if (detail == null) return List.of();
        Object m = detail.get("members");
        return m instanceof List ? (List<Map<String, Object>>) m : List.of();
    }

    // ---- accessors ----
    public List<Map<String, Object>> getCourses()          { return courses; }
    public List<Map<String, Object>> getGroups()           { return groups; }
    public Map<String, Object>       getDetail()           { return detail; }
    public List<Map<String, Object>> getMessages()         { return messages; }

    public String getSelectedCourseId()                    { return selectedCourseId; }
    public void   setSelectedCourseId(String id)           { this.selectedCourseId = id; }
    public String getSelectedGroupId()                     { return selectedGroupId; }
    public void   setSelectedGroupId(String id)            { this.selectedGroupId = id; }
    public String getNewGroupName()                        { return newGroupName; }
    public void   setNewGroupName(String n)                { this.newGroupName = n; }
    public int    getNewGroupMaxMembers()                  { return newGroupMaxMembers; }
    public void   setNewGroupMaxMembers(int n)             { this.newGroupMaxMembers = n; }
    public String getNewMessage()                          { return newMessage; }
    public void   setNewMessage(String m)                  { this.newMessage = m; }
}
