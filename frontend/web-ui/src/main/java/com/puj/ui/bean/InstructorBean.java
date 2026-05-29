package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@Named
@RequestScoped
public class InstructorBean {

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    private String  courseId;
    private String  moduleId;

    private String  newModuleTitle;
    private String  newModuleDescription;

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
            String body = api.toJson(Map.of(
                    "title",       newModuleTitle       != null ? newModuleTitle       : "",
                    "description", newModuleDescription != null ? newModuleDescription : ""
            ));
            HttpResponse<String> resp = api.post(
                    COURSE_URL + "/api/v1/courses/" + courseId + "/modules",
                    body, session.getAccessToken());
            if (resp.statusCode() == 201) {
                FacesMessageUtil.info("Módulo creado exitosamente.");
                return "course-detail?faces-redirect=true&courseId=" + courseId;
            }
            JsonNode err = api.readTree(resp.body());
            FacesMessageUtil.warn(err.path("message").asText("No se pudo crear el módulo."));
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al crear el módulo.");
        }
        return null;
    }

    public String createLesson() {
        if (moduleId == null) return null;
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",           newLessonTitle    != null ? newLessonTitle : "");
            bodyMap.put("durationMinutes", newLessonDuration != null ? newLessonDuration : 0);
            bodyMap.put("supplementary",   newLessonSupplementary);
            HttpResponse<String> resp = api.post(
                    COURSE_URL + "/api/v1/modules/" + moduleId + "/lessons",
                    api.toJson(bodyMap), session.getAccessToken());
            if (resp.statusCode() == 201) {
                JsonNode created    = api.readTree(resp.body());
                String   newLessonId = created.path("id").asText("");
                FacesMessageUtil.info("Lección creada. Ahora agrega sus contenidos.");
                if (!newLessonId.isBlank()) {
                    return "lesson-edit?faces-redirect=true&lessonId=" + newLessonId
                            + "&courseId=" + (courseId != null ? courseId : "");
                }
                return courseId != null
                        ? "course-detail?faces-redirect=true&courseId=" + courseId
                        : "courses?faces-redirect=true";
            }
            JsonNode err = api.readTree(resp.body());
            FacesMessageUtil.warn(err.path("message").asText("No se pudo crear la lección."));
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al crear la lección.");
        }
        return null;
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
    public Integer getNewLessonDuration()                { return newLessonDuration; }
    public void    setNewLessonDuration(Integer d)       { this.newLessonDuration = d; }
    public boolean isNewLessonSupplementary()            { return newLessonSupplementary; }
    public void    setNewLessonSupplementary(boolean s)  { this.newLessonSupplementary = s; }
}
