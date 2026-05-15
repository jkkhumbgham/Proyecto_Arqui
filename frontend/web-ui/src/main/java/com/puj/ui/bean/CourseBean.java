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
import java.util.List;
import java.util.Map;

@Named
@RequestScoped
public class CourseBean {

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");

    @Inject private SessionBean session;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private List<Map<String, Object>> courses = new ArrayList<>();

    // Selected course detail
    private String selectedCourseId;
    private Map<String, Object> courseDetail;
    private List<Map<String, Object>> modules = new ArrayList<>();

    // New course form fields
    private String newTitle;
    private String newDescription;
    private int    newMaxStudents = 30;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;

        if (session.hasRole("INSTRUCTOR", "ADMIN", "DIRECTOR")) {
            loadMyCourses();
        } else {
            loadPublicCourses();
        }
    }

    private void loadPublicCourses() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/courses"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            populateCourses(http.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (Exception e) {
            warn("No se pudo cargar el catálogo de cursos.");
        }
    }

    private void loadMyCourses() {
        try {
            HttpRequest req = bearer(COURSE_URL + "/api/v1/courses/my");
            populateCourses(http.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (Exception e) {
            warn("No se pudo cargar tus cursos.");
        }
    }

    private void populateCourses(HttpResponse<String> resp) throws Exception {
        if (resp.statusCode() == 200) {
            JsonNode arr = mapper.readTree(resp.body());
            arr.forEach(n -> courses.add(Map.of(
                    "id",          n.path("id").asText(),
                    "title",       n.path("title").asText(),
                    "description", n.path("description").asText(""),
                    "status",      n.path("status").asText(),
                    "maxStudents", n.path("maxStudents").asInt()
            )));
        }
    }

    public void loadDetail() {
        if (selectedCourseId == null || selectedCourseId.isBlank()) return;
        try {
            HttpRequest req = bearer(COURSE_URL + "/api/v1/courses/" + selectedCourseId);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                courseDetail = Map.of(
                        "id",          n.path("id").asText(),
                        "title",       n.path("title").asText(),
                        "description", n.path("description").asText(""),
                        "status",      n.path("status").asText()
                );
                modules.clear();
                n.path("modules").forEach(m -> modules.add(Map.of(
                        "id",    m.path("id").asText(),
                        "title", m.path("title").asText(),
                        "order", m.path("orderIndex").asInt()
                )));
            }
        } catch (Exception e) {
            warn("No se pudo cargar el detalle del curso.");
        }
    }

    public String enroll() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/enrollments/courses/" + selectedCourseId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                return "dashboard?faces-redirect=true";
            }
            JsonNode err = mapper.readTree(resp.body());
            warn(err.path("message").asText("No se pudo inscribir."));
        } catch (Exception e) {
            warn("Error al inscribirse.");
        }
        return null;
    }

    public String createCourse() {
        try {
            String body = String.format(
                    "{\"title\":\"%s\",\"description\":\"%s\",\"maxStudents\":%d}",
                    newTitle, newDescription, newMaxStudents);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/courses"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                info("Curso creado exitosamente.");
                return "courses?faces-redirect=true";
            }
            JsonNode err = mapper.readTree(resp.body());
            warn(err.path("message").asText("No se pudo crear el curso."));
        } catch (Exception e) {
            warn("Error al crear el curso.");
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

    // ---- accessors ----
    public List<Map<String, Object>> getCourses()     { return courses; }
    public Map<String, Object>       getCourseDetail() { return courseDetail; }
    public List<Map<String, Object>> getModules()     { return modules; }
    public String getSelectedCourseId()               { return selectedCourseId; }
    public void   setSelectedCourseId(String id)      { this.selectedCourseId = id; }
    public String getNewTitle()                       { return newTitle; }
    public void   setNewTitle(String t)               { this.newTitle = t; }
    public String getNewDescription()                 { return newDescription; }
    public void   setNewDescription(String d)         { this.newDescription = d; }
    public int    getNewMaxStudents()                 { return newMaxStudents; }
    public void   setNewMaxStudents(int n)            { this.newMaxStudents = n; }
}
