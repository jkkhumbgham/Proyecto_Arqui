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
public class DashboardBean {

    private static final String ANALYTICS_URL =
            System.getenv().getOrDefault("ANALYTICS_SERVICE_URL", "http://analytics-service:8080");
    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");

    @Inject private SessionBean session;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    // Summary stats (DIRECTOR / ADMIN)
    private long totalUsers;
    private long totalEnrollments;
    private double avgScore;
    private List<Map<String, Object>> topCourses = new ArrayList<>();

    // Student: enrolled courses
    private List<Map<String, Object>> myCourses = new ArrayList<>();

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;

        if (session.hasRole("DIRECTOR", "ADMIN")) {
            loadSummary();
            loadTopCourses();
        } else if (session.hasRole("STUDENT")) {
            loadMyCourses();
        } else if (session.hasRole("INSTRUCTOR")) {
            loadMyCourses();
        }
    }

    private void loadSummary() {
        try {
            HttpRequest req = bearer(ANALYTICS_URL + "/api/v1/analytics/dashboard/summary");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                totalUsers       = n.path("totalUsers").asLong();
                totalEnrollments = n.path("totalEnrollments").asLong();
                avgScore         = n.path("averageScore").asDouble();
            }
        } catch (Exception e) {
            warn("No se pudo cargar el resumen de analíticas.");
        }
    }

    private void loadTopCourses() {
        try {
            HttpRequest req = bearer(ANALYTICS_URL + "/api/v1/analytics/dashboard/top-courses");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> topCourses.add(Map.of(
                        "title",       n.path("courseTitle").asText(),
                        "enrollments", n.path("totalEnrollments").asLong(),
                        "avgScore",    n.path("averageScore").asDouble()
                )));
            }
        } catch (Exception e) {
            warn("No se pudo cargar el ranking de cursos.");
        }
    }

    private void loadMyCourses() {
        try {
            HttpRequest req = bearer(COURSE_URL + "/api/v1/enrollments/my");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> myCourses.add(Map.of(
                        "courseId",    n.path("courseId").asText(),
                        "title",       n.path("courseTitle").asText(),
                        "progress",    n.path("progressPct").asInt(),
                        "status",      n.path("status").asText()
                )));
            }
        } catch (Exception e) {
            warn("No se pudo cargar tus cursos.");
        }
    }

    private HttpRequest bearer(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private void warn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }

    public long getTotalUsers()       { return totalUsers; }
    public long getTotalEnrollments() { return totalEnrollments; }
    public double getAvgScore()       { return avgScore; }
    public List<Map<String, Object>> getTopCourses() { return topCourses; }
    public List<Map<String, Object>> getMyCourses()  { return myCourses; }
}
