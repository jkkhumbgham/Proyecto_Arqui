package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.*;

@Named
@ViewScoped
public class AdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");
    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");
    private static final String COLLAB_URL =
            System.getenv().getOrDefault("COLLABORATION_SERVICE_URL", "http://collaboration-service:8080");
    private static final String ANALYTICS_URL =
            System.getenv().getOrDefault("ANALYTICS_SERVICE_URL", "http://analytics-service:8080");
    private static final String EMAIL_URL =
            System.getenv().getOrDefault("EMAIL_SERVICE_URL", "http://email-service:8080");

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

    public static class UserRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, email, firstName, lastName, role, lastLoginAt;
        private final boolean active;
        public UserRow(String id, String email, String firstName, String lastName,
                        String role, boolean active, String lastLoginAt) {
            this.id = id; this.email = email; this.firstName = firstName;
            this.lastName = lastName; this.role = role; this.active = active;
            this.lastLoginAt = lastLoginAt;
        }
        public String  getId()          { return id; }
        public String  getEmail()       { return email; }
        public String  getFirstName()   { return firstName; }
        public String  getLastName()    { return lastName; }
        public String  getRole()        { return role; }
        public boolean isActive()       { return active; }
        public String  getLastLoginAt() { return lastLoginAt; }
    }

    public static class HealthStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String service, status;
        private final long   responseMs;
        public HealthStatus(String service, String status, long responseMs) {
            this.service = service; this.status = status; this.responseMs = responseMs;
        }
        public String  getService()    { return service; }
        public String  getStatus()     { return status; }
        public long    getResponseMs() { return responseMs; }
        public boolean isUp()          { return "UP".equals(status); }
    }

    public static class CourseStatRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String courseId, courseTitle;
        private final long   count;
        public CourseStatRow(String courseId, String courseTitle, long count) {
            this.courseId = courseId; this.courseTitle = courseTitle; this.count = count;
        }
        public String getCourseId()    { return courseId; }
        public String getCourseTitle() { return courseTitle; }
        public long   getCount()       { return count; }
    }

    private List<UserRow>      users         = new ArrayList<>();
    private List<HealthStatus> healthChecks  = new ArrayList<>();
    private Map<String, String> selectedRoles = new HashMap<>();
    private long               totalUsers;
    private int                page          = 0;
    private static final int   PAGE_SIZE     = 20;

    // Course stats
    private List<CourseStatRow> popularCourses            = new ArrayList<>();
    private List<CourseStatRow> completedCourses          = new ArrayList<>();
    private long                totalEnrollments          = 0;
    private long                totalCompletedEnrollments = 0;

    // Create user form fields
    private String  newEmail, newPassword, newFirstName, newLastName;
    private String  newRole    = "STUDENT";
    private boolean newConsent = true;

    // Edit user form fields
    private String editUserId, editEmail, editFirstName, editLastName, editRole, editPassword;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        try { page = Integer.parseInt(params.getOrDefault("page", "0")); }
        catch (NumberFormatException e) { page = 0; }
        loadUsers();
        loadHealthChecks();
        loadCourseStats();
    }

    private void loadUsers() {
        users.clear();
        selectedRoles.clear();
        try {
            String url = USER_URL + "/api/v1/users?page=" + page + "&size=" + PAGE_SIZE;
            HttpResponse<String> resp = http().send(bearer(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper().readTree(resp.body());
                totalUsers = root.path("total").asLong(0);
                root.path("data").forEach(n -> {
                    String id   = n.path("id").asText();
                    String role = n.path("role").asText("STUDENT");
                    users.add(new UserRow(
                            id,
                            n.path("email").asText(),
                            n.path("firstName").asText(),
                            n.path("lastName").asText(),
                            role,
                            n.path("active").asBoolean(true),
                            n.path("lastLoginAt").asText("—")));
                    selectedRoles.put(id, role);
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar la lista de usuarios.");
        }
    }

    private void loadHealthChecks() {
        healthChecks.clear();
        checkHealth("user-service",          USER_URL       + "/api/v1/health");
        checkHealth("course-service",        COURSE_URL     + "/api/v1/health");
        checkHealth("assessment-service",    ASSESSMENT_URL + "/api/v1/health");
        checkHealth("collaboration-service", COLLAB_URL     + "/api/v1/health");
        checkHealth("analytics-service",     ANALYTICS_URL  + "/health");
        checkHealth("email-service",         EMAIL_URL      + "/health");
    }

    private void loadCourseStats() {
        popularCourses.clear();
        completedCourses.clear();
        try {
            String url = COURSE_URL + "/api/v1/enrollments/admin/course-stats?limit=5";
            HttpResponse<String> resp = http().send(bearer(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper().readTree(resp.body());
                totalEnrollments          = root.path("totalEnrollments").asLong(0);
                totalCompletedEnrollments = root.path("totalCompletedEnrollments").asLong(0);
                root.path("popularCourses").forEach(n -> popularCourses.add(
                        new CourseStatRow(
                                n.path("courseId").asText(),
                                n.path("courseTitle").asText(),
                                n.path("enrollCount").asLong(0))));
                root.path("completedCourses").forEach(n -> completedCourses.add(
                        new CourseStatRow(
                                n.path("courseId").asText(),
                                n.path("courseTitle").asText(),
                                n.path("completedCount").asLong(0))));
            }
        } catch (Exception e) {
            warn("No se pudo cargar estadísticas de cursos.");
        }
    }

    private void checkHealth(String name, String url) {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .GET().timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            long ms = System.currentTimeMillis() - start;
            JsonNode n = mapper().readTree(resp.body());
            String status = n.path("status").asText("UNKNOWN");
            healthChecks.add(new HealthStatus(name, resp.statusCode() == 200 ? status : "DOWN", ms));
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            healthChecks.add(new HealthStatus(name, "DOWN", ms));
        }
    }

    public String createUser() {
        if (newEmail == null || newEmail.isBlank() || newPassword == null || newPassword.isBlank()) {
            warn("Email y contraseña son obligatorios.");
            return null;
        }
        try {
            ObjectNode body = mapper().createObjectNode();
            body.put("email",        newEmail.trim());
            body.put("password",     newPassword);
            body.put("firstName",    newFirstName != null ? newFirstName.trim() : "");
            body.put("lastName",     newLastName  != null ? newLastName.trim()  : "");
            body.put("role",         newRole != null ? newRole : "STUDENT");
            body.put("consentGiven", newConsent);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USER_URL + "/api/v1/users"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper().writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                info("Usuario creado exitosamente.");
                newEmail = newPassword = newFirstName = newLastName = null;
                newRole = "STUDENT";
                loadUsers();
            } else {
                JsonNode err = mapper().readTree(resp.body());
                warn("Error al crear usuario: " + err.path("message").asText(resp.body()));
            }
        } catch (Exception e) {
            warn("Error al crear usuario: " + e.getMessage());
        }
        return null;
    }

    public String changeRole(String userId) {
        String role = selectedRoles.get(userId);
        if (role == null || role.isBlank()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USER_URL + "/api/v1/users/" + userId + "/role?role=" + role))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 204) {
                warn("Error al cambiar rol.");
            } else {
                info("Rol actualizado.");
                // Refresh list to reflect change
                loadUsers();
            }
        } catch (Exception e) {
            warn("Error al cambiar rol: " + e.getMessage());
        }
        return null;
    }

    public String startEdit(String userId) {
        users.stream().filter(u -> u.getId().equals(userId)).findFirst().ifPresent(u -> {
            editUserId    = u.getId();
            editEmail     = u.getEmail();
            editFirstName = u.getFirstName();
            editLastName  = u.getLastName();
            editRole      = u.getRole();
            editPassword  = null;
        });
        return null;
    }

    public String cancelEdit() {
        editUserId = editEmail = editFirstName = editLastName = editRole = editPassword = null;
        return null;
    }

    public String saveUser() {
        if (editUserId == null) return null;
        try {
            // Update profile (name + optional password)
            ObjectNode body = mapper().createObjectNode();
            body.put("firstName", editFirstName != null ? editFirstName.trim() : "");
            body.put("lastName",  editLastName  != null ? editLastName.trim()  : "");
            if (editPassword != null && !editPassword.isBlank()) {
                body.put("newPassword", editPassword);
            }
            HttpRequest profileReq = HttpRequest.newBuilder()
                    .uri(URI.create(USER_URL + "/api/v1/users/" + editUserId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper().writeValueAsString(body)))
                    .build();
            HttpResponse<String> profileResp = http().send(profileReq, HttpResponse.BodyHandlers.ofString());
            if (profileResp.statusCode() != 200) {
                JsonNode err = mapper().readTree(profileResp.body());
                warn("Error al actualizar perfil: " + err.path("message").asText(profileResp.body()));
                return null;
            }

            // Update role
            if (editRole != null && !editRole.isBlank()) {
                HttpRequest roleReq = HttpRequest.newBuilder()
                        .uri(URI.create(USER_URL + "/api/v1/users/" + editUserId + "/role?role=" + editRole))
                        .header("Authorization", "Bearer " + session.getAccessToken())
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();
                http().send(roleReq, HttpResponse.BodyHandlers.ofString());
            }

            info("Usuario actualizado correctamente.");
            cancelEdit();
            loadUsers();
        } catch (Exception e) {
            warn("Error al guardar: " + e.getMessage());
        }
        return null;
    }

    public String deleteUser(String userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USER_URL + "/api/v1/users/" + userId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .DELETE().build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 204) {
                warn("Error al eliminar usuario.");
            } else {
                info("Usuario eliminado.");
                users.removeIf(u -> u.getId().equals(userId));
                selectedRoles.remove(userId);
                totalUsers = Math.max(0, totalUsers - 1);
            }
        } catch (Exception e) {
            warn("Error: " + e.getMessage());
        }
        return null;
    }

    private HttpRequest bearer(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .GET().timeout(Duration.ofSeconds(5)).build();
    }

    private void warn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }
    private void info(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    // Getters / setters
    public List<UserRow>       getUsers()         { return users; }
    public List<HealthStatus>  getHealthChecks()  { return healthChecks; }
    public List<CourseStatRow> getPopularCourses()          { return popularCourses; }
    public List<CourseStatRow> getCompletedCourses()        { return completedCourses; }
    public long getTotalEnrollments()                        { return totalEnrollments; }
    public long getTotalCompletedEnrollments()              { return totalCompletedEnrollments; }
    public Map<String, String> getSelectedRoles() { return selectedRoles; }
    public void setSelectedRoles(Map<String, String> m) { this.selectedRoles = m; }

    public long    getTotalUsers() { return totalUsers; }
    public int     getPage()       { return page; }
    public int     getNextPage()   { return page + 1; }
    public int     getPrevPage()   { return Math.max(0, page - 1); }
    public boolean isHasPrev()     { return page > 0; }
    public boolean isHasNext()     { return (long)(page + 1) * PAGE_SIZE < totalUsers; }

    public String  getNewEmail()     { return newEmail; }
    public String  getNewPassword()  { return newPassword; }
    public String  getNewFirstName() { return newFirstName; }
    public String  getNewLastName()  { return newLastName; }
    public String  getNewRole()      { return newRole; }
    public boolean isNewConsent()    { return newConsent; }

    public void setNewEmail(String v)     { this.newEmail     = v; }
    public void setNewPassword(String v)  { this.newPassword  = v; }
    public void setNewFirstName(String v) { this.newFirstName = v; }
    public void setNewLastName(String v)  { this.newLastName  = v; }
    public void setNewRole(String v)      { this.newRole      = v; }
    public void setNewConsent(boolean v)  { this.newConsent   = v; }

    public String getEditUserId()    { return editUserId; }
    public String getEditEmail()     { return editEmail; }
    public String getEditFirstName() { return editFirstName; }
    public String getEditLastName()  { return editLastName; }
    public String getEditRole()      { return editRole; }
    public String getEditPassword()  { return editPassword; }

    public void setEditFirstName(String v) { this.editFirstName = v; }
    public void setEditLastName(String v)  { this.editLastName  = v; }
    public void setEditRole(String v)      { this.editRole      = v; }
    public void setEditPassword(String v)  { this.editPassword  = v; }
}
