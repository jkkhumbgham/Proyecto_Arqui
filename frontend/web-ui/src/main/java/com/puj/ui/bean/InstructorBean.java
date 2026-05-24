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
public class InstructorBean {

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");

    @Inject private SessionBean session;

    private final HttpClient   http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    // Context from URL
    private String courseId;
    private String moduleId;

    // Module creation form
    private String newModuleTitle;
    private String newModuleDescription;

    // Lesson creation form
    private String  newLessonTitle;
    private Integer newLessonDuration;
    private boolean newLessonSupplementary = false;

    @PostConstruct
    public void load() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        courseId = params.get("courseId");
        moduleId = params.get("moduleId");
    }

    public String createModule() {
        if (courseId == null) return null;
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "title",       newModuleTitle != null ? newModuleTitle : "",
                    "description", newModuleDescription != null ? newModuleDescription : ""
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/courses/" + courseId + "/modules"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                info("Módulo creado exitosamente.");
                return "course-detail?faces-redirect=true&courseId=" + courseId;
            }
            JsonNode err = mapper.readTree(resp.body());
            warn(err.path("message").asText("No se pudo crear el módulo."));
        } catch (Exception e) {
            warn("Error al crear el módulo.");
        }
        return null;
    }

    public String createLesson() {
        if (moduleId == null) return null;
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",           newLessonTitle != null ? newLessonTitle : "");
            bodyMap.put("durationMinutes", newLessonDuration != null ? newLessonDuration : 0);
            bodyMap.put("supplementary",   newLessonSupplementary);
            String body = mapper.writeValueAsString(bodyMap);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/modules/" + moduleId + "/lessons"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                JsonNode created = mapper.readTree(resp.body());
                String newLessonId = created.path("id").asText("");
                info("Lección creada. Ahora agrega sus contenidos.");
                if (!newLessonId.isBlank()) {
                    return "lesson-edit?faces-redirect=true&lessonId=" + newLessonId
                            + "&courseId=" + (courseId != null ? courseId : "");
                }
                return courseId != null
                        ? "course-detail?faces-redirect=true&courseId=" + courseId
                        : "courses?faces-redirect=true";
            }
            JsonNode err = mapper.readTree(resp.body());
            warn(err.path("message").asText("No se pudo crear la lección."));
        } catch (Exception e) {
            warn("Error al crear la lección.");
        }
        return null;
    }

    private void warn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }

    private void info(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    public String getCourseId()               { return courseId; }
    public void   setCourseId(String id)      { this.courseId = id; }
    public String getModuleId()               { return moduleId; }
    public void   setModuleId(String id)      { this.moduleId = id; }
    public String getNewModuleTitle()         { return newModuleTitle; }
    public void   setNewModuleTitle(String t) { this.newModuleTitle = t; }
    public String getNewModuleDescription()         { return newModuleDescription; }
    public void   setNewModuleDescription(String d) { this.newModuleDescription = d; }
    public String  getNewLessonTitle()        { return newLessonTitle; }
    public void    setNewLessonTitle(String t){ this.newLessonTitle = t; }
    public Integer getNewLessonDuration()                   { return newLessonDuration; }
    public void    setNewLessonDuration(Integer d)          { this.newLessonDuration = d; }
    public boolean isNewLessonSupplementary()               { return newLessonSupplementary; }
    public void    setNewLessonSupplementary(boolean s)     { this.newLessonSupplementary = s; }
}
