package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@RequestScoped
public class AssessmentBean {

    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean session;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private List<Map<String, Object>> assessments = new ArrayList<>();

    private String  selectedAssessmentId;
    private Map<String, Object> assessmentDetail;
    private List<Map<String, Object>> questions = new ArrayList<>();

    // Answers indexed by questionId
    private Map<String, String>  selectedAnswers  = new HashMap<>();
    private Map<String, Boolean> selectedBooleans = new HashMap<>();

    // Active submission
    private String submissionId;

    // Result after grading
    private Map<String, Object> submissionResult;
    private String adaptiveRecommendation;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        loadAssessments();
    }

    private void loadAssessments() {
        try {
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/assessments");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> assessments.add(Map.of(
                        "id",         n.path("id").asText(),
                        "title",      n.path("title").asText(),
                        "courseId",   n.path("courseId").asText(),
                        "totalPoints",n.path("totalPoints").asInt()
                )));
            }
        } catch (Exception e) {
            warn("No se pudo cargar las evaluaciones.");
        }
    }

    public void loadDetail() {
        if (selectedAssessmentId == null) return;
        try {
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/assessments/" + selectedAssessmentId);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                assessmentDetail = Map.of(
                        "id",          n.path("id").asText(),
                        "title",       n.path("title").asText(),
                        "description", n.path("description").asText(""),
                        "totalPoints", n.path("totalPoints").asInt()
                );
                questions.clear();
                n.path("questions").forEach(q -> {
                    List<Map<String, String>> opts = new ArrayList<>();
                    q.path("options").forEach(o -> opts.add(Map.of(
                            "id",   o.path("id").asText(),
                            "text", o.path("text").asText()
                    )));
                    Map<String, Object> qMap = new HashMap<>();
                    qMap.put("id",     q.path("id").asText());
                    qMap.put("text",   q.path("questionText").asText());
                    qMap.put("type",   q.path("questionType").asText());
                    qMap.put("points", q.path("points").asInt());
                    qMap.put("options", opts);
                    questions.add(qMap);
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar la evaluación.");
        }
    }

    public String startSubmission() {
        try {
            String body = String.format("{\"assessmentId\":\"%s\"}", selectedAssessmentId);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ASSESSMENT_URL + "/api/v1/submissions"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                submissionId = mapper.readTree(resp.body()).path("id").asText();
                return "assessment-take?faces-redirect=true&submissionId=" + submissionId;
            }
            warn("No se pudo iniciar la evaluación.");
        } catch (Exception e) {
            warn("Error al iniciar la evaluación.");
        }
        return null;
    }

    public String submitAnswers() {
        if (submissionId == null) return null;
        try {
            ArrayNode answersArray = mapper.createArrayNode();
            selectedAnswers.forEach((qId, optId) -> {
                ObjectNode a = mapper.createObjectNode();
                a.put("questionId", qId);
                a.put("selectedOptionId", optId);
                answersArray.add(a);
            });
            selectedBooleans.forEach((qId, val) -> {
                ObjectNode a = mapper.createObjectNode();
                a.put("questionId", qId);
                a.put("booleanAnswer", val);
                answersArray.add(a);
            });

            String body = mapper.createObjectNode()
                    .set("answers", answersArray).toString();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ASSESSMENT_URL + "/api/v1/submissions/" + submissionId + "/submit"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode result = mapper.readTree(resp.body());
                submissionResult = Map.of(
                        "score",    result.path("score").asDouble(),
                        "passed",   result.path("passed").asBoolean(),
                        "status",   result.path("status").asText()
                );
                adaptiveRecommendation = result.path("adaptiveRecommendation").asText("");
                return "assessment-result?faces-redirect=true";
            }
            warn("Error al enviar respuestas.");
        } catch (Exception e) {
            warn("Error de conexión al enviar.");
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

    // ---- accessors ----
    public List<Map<String, Object>> getAssessments()   { return assessments; }
    public Map<String, Object>  getAssessmentDetail()   { return assessmentDetail; }
    public List<Map<String, Object>> getQuestions()     { return questions; }
    public Map<String, Object>  getSubmissionResult()   { return submissionResult; }
    public String getAdaptiveRecommendation()           { return adaptiveRecommendation; }
    public String getSelectedAssessmentId()             { return selectedAssessmentId; }
    public void   setSelectedAssessmentId(String id)    { this.selectedAssessmentId = id; }
    public String getSubmissionId()                     { return submissionId; }
    public void   setSubmissionId(String id)            { this.submissionId = id; }
    public Map<String, String>  getSelectedAnswers()    { return selectedAnswers; }
    public Map<String, Boolean> getSelectedBooleans()   { return selectedBooleans; }
}
