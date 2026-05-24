package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.ui.service.MinioUploadService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

import java.io.Serializable;
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
@ViewScoped
public class LessonContentBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");

    @Inject private SessionBean      session;
    @Inject private MinioUploadService minioUpload;

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

    private String lessonId;
    private String courseId;
    private String lessonTitle;

    // Add-content form
    private String newTitle;
    private String newDescription;
    private String newContentType = "TEXT";
    private String newContentUrl;
    private transient Part uploadedFile;

    // Edit-content state
    private String editContentId;
    private String editTitle;
    private String editDescription;
    private String editContentType = "TEXT";
    private String editContentUrl;

    private List<ContentInfo> contents = new ArrayList<>();

    public static class ContentInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, title, description, contentType, contentUrl;
        private final int orderIndex;

        public ContentInfo(String id, String title, String description,
                           String contentType, String contentUrl, int orderIndex) {
            this.id = id; this.title = title; this.description = description;
            this.contentType = contentType; this.contentUrl = contentUrl;
            this.orderIndex = orderIndex;
        }
        public String getId()          { return id; }
        public String getTitle()       { return title; }
        public String getDescription() { return description; }
        public String getContentType() { return contentType; }
        public String getContentUrl()  { return contentUrl; }
        public int    getOrderIndex()  { return orderIndex; }
    }

    @PostConstruct
    public void load() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        lessonId      = params.get("lessonId");
        courseId      = params.get("courseId");
        editContentId = params.get("editContentId");
        if (lessonId == null || lessonId.isBlank()) return;
        loadLessonTitle();
        loadContents();
        if (editContentId != null && !editContentId.isBlank()) {
            contents.stream().filter(c -> c.getId().equals(editContentId)).findFirst().ifPresent(c -> {
                editTitle       = c.getTitle();
                editDescription = c.getDescription();
                editContentType = c.getContentType() != null ? c.getContentType() : "TEXT";
                editContentUrl  = c.getContentUrl();
            });
        }
    }

    private void loadLessonTitle() {
        try {
            HttpResponse<String> resp = http().send(bearer(COURSE_URL + "/api/v1/lessons/" + lessonId),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                lessonTitle = mapper().readTree(resp.body()).path("title").asText();
            }
        } catch (Exception ignored) {}
    }

    private void loadContents() {
        try {
            HttpResponse<String> resp = http().send(
                    bearer(COURSE_URL + "/api/v1/lessons/" + lessonId + "/contents"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper().readTree(resp.body());
                contents.clear();
                if (arr.isArray()) {
                    arr.forEach(n -> contents.add(new ContentInfo(
                            n.path("id").asText(),
                            n.path("title").asText(),
                            n.path("description").asText(""),
                            n.path("contentType").asText("TEXT"),
                            n.path("contentUrl").isNull() ? null : n.path("contentUrl").asText(null),
                            n.path("orderIndex").asInt()
                    )));
                }
            }
        } catch (Exception ignored) {}
    }

    public String addContent() {
        if (lessonId == null) return null;
        try {
            if (uploadedFile != null && uploadedFile.getSize() > 0) {
                if (!minioUpload.isAvailable()) {
                    warn("Servicio de almacenamiento no disponible.");
                    return null;
                }
                newContentUrl = minioUpload.upload(uploadedFile);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("title",       newTitle != null ? newTitle.trim() : "Sin título");
            body.put("description", newDescription != null ? newDescription.trim() : "");
            body.put("contentType", newContentType != null ? newContentType : "TEXT");
            if (newContentUrl != null && !newContentUrl.isBlank()) {
                body.put("contentUrl", newContentUrl.trim());
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/lessons/" + lessonId + "/contents"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper().writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(10)).build();

            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                return "lesson-edit?faces-redirect=true&lessonId=" + lessonId + "&courseId=" + courseId;
            }
            warn("No se pudo agregar el contenido: " + resp.statusCode());
        } catch (Exception e) {
            warn("Error al agregar contenido: " + e.getMessage());
        }
        return null;
    }

    public String updateContent() {
        if (editContentId == null || lessonId == null) return null;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("title",       editTitle != null ? editTitle.trim() : "Sin título");
            body.put("description", editDescription != null ? editDescription.trim() : "");
            body.put("contentType", editContentType != null ? editContentType : "TEXT");
            if (editContentUrl != null && !editContentUrl.isBlank()) {
                body.put("contentUrl", editContentUrl.trim());
            } else {
                body.put("contentUrl", null);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/lessons/" + lessonId + "/contents/" + editContentId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper().writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(10)).build();

            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return "lesson-edit?faces-redirect=true&lessonId=" + lessonId + "&courseId=" + courseId;
            }
            warn("No se pudo actualizar el contenido: " + resp.statusCode());
        } catch (Exception e) {
            warn("Error al actualizar contenido: " + e.getMessage());
        }
        return null;
    }

    public String deleteContent(String contentId) {
        if (contentId == null || lessonId == null) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/lessons/" + lessonId + "/contents/" + contentId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .DELETE().timeout(Duration.ofSeconds(5)).build();
            http().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
        return "lesson-edit?faces-redirect=true&lessonId=" + lessonId + "&courseId=" + courseId;
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

    public String  getLessonId()           { return lessonId; }
    public void    setLessonId(String v)   { this.lessonId = v; }
    public String  getCourseId()           { return courseId; }
    public void    setCourseId(String v)   { this.courseId = v; }
    public String  getLessonTitle()        { return lessonTitle; }
    public List<ContentInfo> getContents() { return contents; }
    public boolean hasContents()     { return !contents.isEmpty(); }

    public String  getNewTitle()        { return newTitle; }
    public void    setNewTitle(String v){ this.newTitle = v; }
    public String  getNewDescription()  { return newDescription; }
    public void    setNewDescription(String v){ this.newDescription = v; }
    public String  getNewContentType()  { return newContentType; }
    public void    setNewContentType(String v){ this.newContentType = v; }
    public String  getNewContentUrl()   { return newContentUrl; }
    public void    setNewContentUrl(String v){ this.newContentUrl = v; }
    public Part    getUploadedFile()    { return uploadedFile; }
    public void    setUploadedFile(Part f){ this.uploadedFile = f; }

    public boolean isEditMode()                     { return editContentId != null && !editContentId.isBlank(); }
    public String  getEditContentId()               { return editContentId; }
    public void    setEditContentId(String v)       { this.editContentId = v; }
    public String  getEditTitle()                   { return editTitle; }
    public void    setEditTitle(String v)           { this.editTitle = v; }
    public String  getEditDescription()             { return editDescription; }
    public void    setEditDescription(String v)     { this.editDescription = v; }
    public String  getEditContentType()             { return editContentType; }
    public void    setEditContentType(String v)     { this.editContentType = v; }
    public String  getEditContentUrl()              { return editContentUrl; }
    public void    setEditContentUrl(String v)      { this.editContentUrl = v; }
}
