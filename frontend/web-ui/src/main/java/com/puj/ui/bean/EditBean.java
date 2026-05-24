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
import java.util.HashMap;
import java.util.Map;

@Named
@RequestScoped
public class EditBean {

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean session;

    private final HttpClient   http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    // URL context
    private String courseId  = "";
    private String moduleId  = "";
    private String lessonId  = "";

    // Course edit fields
    private String  courseTitle;
    private String  courseDescription;
    private int     courseMaxStudents = 30;
    private String  courseStatus      = "DRAFT";

    // Module edit fields
    private String moduleTitle;
    private String moduleDescription;

    // Lesson edit fields
    private String  lessonTitle;
    private Integer lessonDuration;

    @PostConstruct
    public void load() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        courseId = params.getOrDefault("courseId", "");
        moduleId = params.getOrDefault("moduleId", "");
        lessonId = params.getOrDefault("lessonId", "");

        if (!moduleId.isBlank())      loadModule();
        else if (!lessonId.isBlank()) loadLesson();
        else if (!courseId.isBlank()) loadCourse();
    }

    // ── loaders ──────────────────────────────────────────────────────────────

    private void loadCourse() {
        try {
            HttpResponse<String> resp = get(COURSE_URL + "/api/v1/courses/" + courseId);
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                courseTitle       = n.path("title").asText("");
                courseDescription = n.path("description").asText("");
                courseMaxStudents = n.path("maxStudents").asInt(30);
                courseStatus      = n.path("status").asText("DRAFT");
            }
        } catch (Exception e) { warn("No se pudo cargar el curso."); }
    }

    private void loadModule() {
        try {
            HttpResponse<String> resp = get(COURSE_URL + "/api/v1/modules/" + moduleId);
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                moduleTitle       = n.path("title").asText("");
                moduleDescription = n.path("description").asText("");
                if (courseId.isBlank()) courseId = n.path("courseId").asText("");
            }
        } catch (Exception e) { warn("No se pudo cargar el módulo."); }
    }

    private void loadLesson() {
        try {
            HttpResponse<String> resp = get(COURSE_URL + "/api/v1/lessons/" + lessonId);
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                lessonTitle    = n.path("title").asText("");
                lessonDuration = n.path("durationMinutes").isNull() ? null : n.path("durationMinutes").asInt();
                if (courseId.isBlank()) courseId = n.path("courseId").asText("");
            }
        } catch (Exception e) { warn("No se pudo cargar la lección."); }
    }

    // ── update actions ────────────────────────────────────────────────────────

    public String updateCourse() {
        if (courseTitle == null || courseTitle.isBlank()) { warn("El título es obligatorio."); return null; }
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",       courseTitle);
            bodyMap.put("description", courseDescription != null ? courseDescription : "");
            bodyMap.put("maxStudents", courseMaxStudents);
            bodyMap.put("status",      courseStatus != null ? courseStatus : "DRAFT");
            String body = mapper.writeValueAsString(bodyMap);
            HttpResponse<String> resp = put(COURSE_URL + "/api/v1/courses/" + courseId, body);
            if (resp.statusCode() == 200) {
                return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
            }
            warn(errorMessage(resp, "No se pudo actualizar el curso."));
        } catch (Exception e) { warn("Error al guardar."); }
        return null;
    }

    public String updateModule() {
        if (moduleTitle == null || moduleTitle.isBlank()) { warn("El título es obligatorio."); return null; }
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",       moduleTitle);
            bodyMap.put("description", moduleDescription != null ? moduleDescription : "");
            String body = mapper.writeValueAsString(bodyMap);
            HttpResponse<String> resp = put(COURSE_URL + "/api/v1/modules/" + moduleId, body);
            if (resp.statusCode() == 200) {
                return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
            }
            warn(errorMessage(resp, "No se pudo actualizar el módulo."));
        } catch (Exception e) { warn("Error al guardar."); }
        return null;
    }

    public String updateLesson() {
        if (lessonTitle == null || lessonTitle.isBlank()) { warn("El título es obligatorio."); return null; }
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title", lessonTitle);
            if (lessonDuration != null) bodyMap.put("durationMinutes", lessonDuration);
            String body = mapper.writeValueAsString(bodyMap);
            HttpResponse<String> resp = put(COURSE_URL + "/api/v1/lessons/" + lessonId, body);
            if (resp.statusCode() == 200) {
                return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
            }
            warn(errorMessage(resp, "No se pudo actualizar la lección."));
        } catch (Exception e) { warn("Error al guardar."); }
        return null;
    }

    // ── delete actions (called with method params from XHTML) ─────────────────

    public String deleteCourse(String id) {
        try {
            HttpResponse<String> resp = delete(COURSE_URL + "/api/v1/courses/" + id);
            if (resp.statusCode() == 204) {
                return "/views/courses?faces-redirect=true";
            }
            warn(errorMessage(resp, "No se pudo eliminar el curso."));
        } catch (Exception e) { warn("Error al eliminar."); }
        return null;
    }

    public String deleteModule(String id, String cid) {
        System.err.println("[DELETE] deleteModule id=" + id + " cid=" + cid);
        try {
            HttpResponse<String> resp = delete(COURSE_URL + "/api/v1/modules/" + id);
            System.err.println("[DELETE] deleteModule HTTP " + resp.statusCode() + " body=" + resp.body());
            if (resp.statusCode() == 204) {
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            warn(errorMessage(resp, "No se pudo eliminar el módulo."));
        } catch (Exception e) {
            System.err.println("[DELETE] deleteModule exception: " + e);
            warn("Error al eliminar.");
        }
        return null;
    }

    public String deleteLesson(String id, String cid) {
        System.err.println("[DELETE] deleteLesson id=" + id + " cid=" + cid);
        try {
            HttpResponse<String> resp = delete(COURSE_URL + "/api/v1/lessons/" + id);
            System.err.println("[DELETE] deleteLesson HTTP " + resp.statusCode() + " body=" + resp.body());
            if (resp.statusCode() == 204) {
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            warn(errorMessage(resp, "No se pudo eliminar la lección."));
        } catch (Exception e) {
            System.err.println("[DELETE] deleteLesson exception: " + e);
            warn("Error al eliminar.");
        }
        return null;
    }

    public String deleteAssessment(String id, String cid) {
        try {
            HttpResponse<String> resp = delete(ASSESSMENT_URL + "/api/v1/assessments/" + id);
            if (resp.statusCode() == 204) {
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            warn(errorMessage(resp, "No se pudo eliminar la evaluación."));
        } catch (Exception e) { warn("Error al eliminar."); }
        return null;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String url) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .DELETE().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String errorMessage(HttpResponse<String> resp, String fallback) {
        try { return mapper.readTree(resp.body()).path("message").asText(fallback); }
        catch (Exception e) { return fallback; }
    }

    private void warn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }

    // ── getters / setters ─────────────────────────────────────────────────────

    public String  getCourseId()              { return courseId; }
    public void    setCourseId(String v)      { this.courseId = v; }
    public String  getModuleId()              { return moduleId; }
    public void    setModuleId(String v)      { this.moduleId = v; }
    public String  getLessonId()              { return lessonId; }
    public void    setLessonId(String v)      { this.lessonId = v; }

    public String  getCourseTitle()           { return courseTitle; }
    public void    setCourseTitle(String v)   { this.courseTitle = v; }
    public String  getCourseDescription()     { return courseDescription; }
    public void    setCourseDescription(String v) { this.courseDescription = v; }
    public int     getCourseMaxStudents()     { return courseMaxStudents; }
    public void    setCourseMaxStudents(int v){ this.courseMaxStudents = v; }
    public String  getCourseStatus()          { return courseStatus; }
    public void    setCourseStatus(String v)  { this.courseStatus = v; }

    public String  getModuleTitle()           { return moduleTitle; }
    public void    setModuleTitle(String v)   { this.moduleTitle = v; }
    public String  getModuleDescription()     { return moduleDescription; }
    public void    setModuleDescription(String v) { this.moduleDescription = v; }

    public String  getLessonTitle()           { return lessonTitle; }
    public void    setLessonTitle(String v)   { this.lessonTitle = v; }
    public Integer getLessonDuration()        { return lessonDuration; }
    public void    setLessonDuration(Integer v){ this.lessonDuration = v; }
}
