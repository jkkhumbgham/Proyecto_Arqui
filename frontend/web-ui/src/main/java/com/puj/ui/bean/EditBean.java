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
import java.util.logging.Logger;

@Named
@RequestScoped
public class EditBean {

    private static final Logger LOG = Logger.getLogger(EditBean.class.getName());

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    private String  courseId  = "";
    private String  moduleId  = "";
    private String  lessonId  = "";

    private String  courseTitle;
    private String  courseDescription;
    private int     courseMaxStudents = 30;
    private String  courseStatus      = "DRAFT";

    private String  moduleTitle;
    private String  moduleDescription;

    private String  lessonTitle;
    private Integer lessonDuration;

    @PostConstruct
    public void load() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        courseId = params.getOrDefault("courseId", "");
        moduleId = params.getOrDefault("moduleId", "");
        lessonId = params.getOrDefault("lessonId", "");

        if      (!moduleId.isBlank()) loadModule();
        else if (!lessonId.isBlank()) loadLesson();
        else if (!courseId.isBlank()) loadCourse();
    }

    private void loadCourse() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/courses/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                courseTitle       = n.path("title").asText("");
                courseDescription = n.path("description").asText("");
                courseMaxStudents = n.path("maxStudents").asInt(30);
                courseStatus      = n.path("status").asText("DRAFT");
            }
        } catch (Exception e) { FacesMessageUtil.warn("No se pudo cargar el curso."); }
    }

    private void loadModule() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/modules/" + moduleId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                moduleTitle       = n.path("title").asText("");
                moduleDescription = n.path("description").asText("");
                if (courseId.isBlank()) courseId = n.path("courseId").asText("");
            }
        } catch (Exception e) { FacesMessageUtil.warn("No se pudo cargar el módulo."); }
    }

    private void loadLesson() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/lessons/" + lessonId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                lessonTitle    = n.path("title").asText("");
                lessonDuration = n.path("durationMinutes").isNull() ? null : n.path("durationMinutes").asInt();
                if (courseId.isBlank()) courseId = n.path("courseId").asText("");
            }
        } catch (Exception e) { FacesMessageUtil.warn("No se pudo cargar la lección."); }
    }

    public String updateCourse() {
        if (courseTitle == null || courseTitle.isBlank()) {
            FacesMessageUtil.warn("El título es obligatorio.");
            return null;
        }
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",       courseTitle);
            bodyMap.put("description", courseDescription != null ? courseDescription : "");
            bodyMap.put("maxStudents", courseMaxStudents);
            bodyMap.put("status",      courseStatus != null ? courseStatus : "DRAFT");
            HttpResponse<String> resp = api.put(COURSE_URL + "/api/v1/courses/" + courseId,
                    api.toJson(bodyMap), session.getAccessToken());
            if (resp.statusCode() == 200) {
                return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
            }
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo actualizar el curso."));
        } catch (Exception e) { FacesMessageUtil.warn("Error al guardar."); }
        return null;
    }

    public String updateModule() {
        if (moduleTitle == null || moduleTitle.isBlank()) {
            FacesMessageUtil.warn("El título es obligatorio.");
            return null;
        }
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",       moduleTitle);
            bodyMap.put("description", moduleDescription != null ? moduleDescription : "");
            HttpResponse<String> resp = api.put(COURSE_URL + "/api/v1/modules/" + moduleId,
                    api.toJson(bodyMap), session.getAccessToken());
            if (resp.statusCode() == 200) {
                return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
            }
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo actualizar el módulo."));
        } catch (Exception e) { FacesMessageUtil.warn("Error al guardar."); }
        return null;
    }

    public String updateLesson() {
        if (lessonTitle == null || lessonTitle.isBlank()) {
            FacesMessageUtil.warn("El título es obligatorio.");
            return null;
        }
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title", lessonTitle);
            if (lessonDuration != null) bodyMap.put("durationMinutes", lessonDuration);
            HttpResponse<String> resp = api.put(COURSE_URL + "/api/v1/lessons/" + lessonId,
                    api.toJson(bodyMap), session.getAccessToken());
            if (resp.statusCode() == 200) {
                return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
            }
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo actualizar la lección."));
        } catch (Exception e) { FacesMessageUtil.warn("Error al guardar."); }
        return null;
    }

    public String deleteCourse(String id) {
        try {
            HttpResponse<String> resp = api.delete(COURSE_URL + "/api/v1/courses/" + id,
                    session.getAccessToken());
            if (resp.statusCode() == 204) return "/views/courses?faces-redirect=true";
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo eliminar el curso."));
        } catch (Exception e) { FacesMessageUtil.warn("Error al eliminar."); }
        return null;
    }

    public String deleteModule(String id, String cid) {
        LOG.fine("[EditBean] deleteModule id=" + id + " cid=" + cid);
        try {
            HttpResponse<String> resp = api.delete(COURSE_URL + "/api/v1/modules/" + id,
                    session.getAccessToken());
            LOG.fine("[EditBean] deleteModule HTTP " + resp.statusCode());
            if (resp.statusCode() == 204) {
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo eliminar el módulo."));
        } catch (Exception e) {
            LOG.warning("[EditBean] deleteModule error: " + e.getMessage());
            FacesMessageUtil.warn("Error al eliminar.");
        }
        return null;
    }

    public String deleteLesson(String id, String cid) {
        LOG.fine("[EditBean] deleteLesson id=" + id + " cid=" + cid);
        try {
            HttpResponse<String> resp = api.delete(COURSE_URL + "/api/v1/lessons/" + id,
                    session.getAccessToken());
            LOG.fine("[EditBean] deleteLesson HTTP " + resp.statusCode());
            if (resp.statusCode() == 204) {
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo eliminar la lección."));
        } catch (Exception e) {
            LOG.warning("[EditBean] deleteLesson error: " + e.getMessage());
            FacesMessageUtil.warn("Error al eliminar.");
        }
        return null;
    }

    public String deleteAssessment(String id, String cid) {
        try {
            HttpResponse<String> resp = api.delete(ASSESSMENT_URL + "/api/v1/assessments/" + id,
                    session.getAccessToken());
            if (resp.statusCode() == 204) {
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            FacesMessageUtil.warn(api.errorMessage(resp, "No se pudo eliminar la evaluación."));
        } catch (Exception e) { FacesMessageUtil.warn("Error al eliminar."); }
        return null;
    }

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
