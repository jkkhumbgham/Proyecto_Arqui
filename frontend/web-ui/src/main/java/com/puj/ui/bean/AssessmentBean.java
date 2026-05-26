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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Named
@RequestScoped
public class AssessmentBean {

    public static class AssessmentData {
        private final String id, title, description, courseId;
        private final double totalPoints;

        // Campos enriquecidos con datos de submissions del estudiante
        private int    maxAttempts     = 3;
        private double passingScorePct = 60.0;
        private int    attemptsUsed    = 0;
        private double bestScorePct    = -1.0; // -1 = sin intentos previos

        public AssessmentData(String id, String title, String description, double totalPoints, String courseId) {
            this.id = id; this.title = title; this.description = description;
            this.totalPoints = totalPoints; this.courseId = courseId;
        }

        // Getters existentes
        public String getId()           { return id; }
        public String getTitle()        { return title; }
        public String getDescription()  { return description; }
        public double getTotalPoints()  { return totalPoints; }
        public String getCourseId()     { return courseId; }

        // Setters y getters nuevos
        public void   setMaxAttempts(int v)        { maxAttempts     = v; }
        public void   setPassingScorePct(double v) { passingScorePct = v; }
        public void   setAttemptsUsed(int v)       { attemptsUsed    = v; }
        public void   setBestScorePct(double v)    { bestScorePct    = v; }

        public int    getMaxAttempts()     { return maxAttempts; }
        public double getPassingScorePct() { return passingScorePct; }
        public int    getAttemptsUsed()    { return attemptsUsed; }
        public double getBestScorePct()    { return bestScorePct; }

        // Booleanos calculados (EL: #{a.attemptsExhausted}, #{a.hasAttempted}, #{a.bestScorePassing})
        public boolean isAttemptsExhausted() { return attemptsUsed >= maxAttempts; }
        public boolean isHasAttempted()      { return attemptsUsed > 0; }
        public boolean isBestScorePassing()  { return bestScorePct >= passingScorePct; }
    }

    public static class CourseGroup {
        private final String courseId, courseTitle;
        private final List<AssessmentData> assessments;
        public CourseGroup(String courseId, String courseTitle, List<AssessmentData> assessments) {
            this.courseId = courseId; this.courseTitle = courseTitle; this.assessments = assessments;
        }
        public String              getCourseId()    { return courseId; }
        public String              getCourseTitle() { return courseTitle; }
        public List<AssessmentData> getAssessments() { return assessments; }
    }

    public static class QuestionData {
        private final String id, text, type;
        private final double points;
        private final List<Map<String, String>> options;
        public QuestionData(String id, String text, String type, double points,
                            List<Map<String, String>> options) {
            this.id = id; this.text = text; this.type = type;
            this.points = points; this.options = options;
        }
        public String                    getId()      { return id; }
        public String                    getText()    { return text; }
        public String                    getType()    { return type; }
        public double                    getPoints()  { return points; }
        public List<Map<String, String>> getOptions() { return options; }
    }

    // Instructor quiz results
    public static class SubmissionResult {
        private final String userId, submittedAt;
        private final double score, maxScore, scorePct;
        private final boolean passed;
        private final int attempt;
        public SubmissionResult(String userId, String submittedAt, double score,
                                double maxScore, double scorePct, boolean passed, int attempt) {
            this.userId = userId; this.submittedAt = submittedAt;
            this.score = score; this.maxScore = maxScore;
            this.scorePct = scorePct; this.passed = passed; this.attempt = attempt;
        }
        public String  getUserId()      { return userId; }
        public String  getSubmittedAt() { return submittedAt; }
        public double  getScore()       { return score; }
        public double  getMaxScore()    { return maxScore; }
        public double  getScorePct()    { return scorePct; }
        public boolean isPassed()       { return passed; }
        public int     getAttempt()     { return attempt; }
    }

    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");
    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Inject private SessionBean session;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private List<CourseGroup> groups = new ArrayList<>();

    private String  selectedAssessmentId;
    private AssessmentData assessmentDetail;
    private List<QuestionData> questions = new ArrayList<>();

    private Map<String, String>   authorNameCache      = new HashMap<>();
    private Map<String, String>   selectedAnswers      = new HashMap<>();
    private Map<String, String[]> selectedMultiAnswers = new HashMap<>();

    private String submissionId;

    private Map<String, Object> submissionResult;
    private String adaptiveRecommendation;
    private String supplementaryLessonId;
    private Map<String, Object> supplementaryLesson;

    // Filter by course (instructor from dashboard)
    private String filteredCourseId;

    // Course to return to when navigating back
    private String backCourseId;

    // Instructor quiz results
    private String quizResultsTitle;
    private List<SubmissionResult> quizResults = new ArrayList<>();
    private String selectedAssessmentForResults;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;

        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();

        // Always capture courseId first so it survives the early return below
        String courseIdParam = params.get("courseId");
        if (courseIdParam != null && !courseIdParam.isBlank()) {
            backCourseId    = courseIdParam;
            filteredCourseId = courseIdParam;
        }

        String assessmentIdParam = params.get("assessmentId");
        String submissionIdParam = params.get("submissionId");
        if (assessmentIdParam != null && !assessmentIdParam.isBlank()) {
            selectedAssessmentId = assessmentIdParam;
            if (submissionIdParam != null && !submissionIdParam.isBlank()) {
                submissionId = submissionIdParam;
            }
            loadDetail();
            // Derive back-course from the assessment itself when not passed in URL
            if (backCourseId == null && assessmentDetail != null
                    && assessmentDetail.getCourseId() != null
                    && !assessmentDetail.getCourseId().isBlank()) {
                backCourseId = assessmentDetail.getCourseId();
            }
            return;
        }

        // Load quiz results if requested (instructor)
        String resultsForParam = params.get("resultsFor");
        if (resultsForParam != null && !resultsForParam.isBlank()
                && session.hasRole("INSTRUCTOR", "ADMIN")) {
            selectedAssessmentForResults = resultsForParam;
            loadQuizResults(resultsForParam);
        }

        loadAssessments();
    }

    private void loadAssessments() {
        try {
            if (session.hasRole("INSTRUCTOR", "ADMIN")) {
                if (filteredCourseId != null) {
                    // Single course from dashboard link
                    loadCourseGroup(filteredCourseId, null);
                } else {
                    // All instructor courses
                    HttpRequest req = bearer(COURSE_URL + "/api/v1/courses/my");
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode root = mapper.readTree(resp.body());
                        JsonNode arr  = root.isArray() ? root : root.path("data");
                        arr.forEach(n -> loadCourseGroup(
                                n.path("id").asText(),
                                n.path("title").asText()));
                    }
                }
            } else {
                // Students: enrolled courses
                Set<String> enrolled = loadEnrolledCourseIds();
                for (String courseId : enrolled) {
                    loadCourseGroup(courseId, null);
                }
                enrichWithStudentStats();
            }
        } catch (Exception e) {
            warn("No se pudo cargar los cursos.");
        }
    }

    private void loadCourseGroup(String courseId, String knownTitle) {
        try {
            // Resolve title when not known (student path or single-course filter)
            String courseTitle = knownTitle;
            if (courseTitle == null || courseTitle.isBlank()) {
                HttpRequest req = bearer(COURSE_URL + "/api/v1/courses/" + courseId);
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                courseTitle = (resp.statusCode() == 200)
                        ? mapper.readTree(resp.body()).path("title").asText("Curso")
                        : "Curso " + courseId.substring(0, 8) + "…";
            }

            // Load assessments for this course
            List<AssessmentData> list = new ArrayList<>();
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/assessments/course/" + courseId);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                mapper.readTree(resp.body()).forEach(n -> {
                    AssessmentData ad = new AssessmentData(
                            n.path("id").asText(),
                            n.path("title").asText(),
                            n.path("description").asText(""),
                            n.path("totalPoints").asDouble(),
                            courseId);
                    ad.setMaxAttempts(n.path("maxAttempts").asInt(3));
                    ad.setPassingScorePct(n.path("passingScorePct").asDouble(60.0));
                    list.add(ad);
                });
            }

            groups.add(new CourseGroup(courseId, courseTitle, list));
        } catch (Exception ignored) {}
    }

    private Set<String> loadEnrolledCourseIds() {
        Set<String> ids = new HashSet<>();
        try {
            HttpRequest req = bearer(COURSE_URL + "/api/v1/enrollments/my");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                mapper.readTree(resp.body())
                        .forEach(n -> ids.add(n.path("courseId").asText()));
            }
        } catch (Exception ignored) {}
        return ids;
    }

    /** Enriquece cada AssessmentData con la mejor calificación e intentos usados del estudiante. */
    private void enrichWithStudentStats() {
        try {
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/submissions/my?size=500");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            // Map<assessmentId, [bestScorePct, maxAttemptNumber]>
            Map<String, double[]> stats = new HashMap<>();
            mapper.readTree(resp.body()).forEach(s -> {
                String aid = s.path("assessmentId").asText();
                double pct = s.path("scorePct").asDouble();
                int    att = s.path("attemptNumber").asInt();
                double[] cur = stats.computeIfAbsent(aid, k -> new double[]{-1.0, 0.0});
                if (pct > cur[0]) cur[0] = pct;  // mejor nota
                if (att > cur[1]) cur[1] = att;  // intentos usados = mayor attemptNumber
            });

            groups.forEach(grp -> grp.getAssessments().forEach(a -> {
                double[] s = stats.getOrDefault(a.getId(), new double[]{-1.0, 0.0});
                a.setBestScorePct(s[0]);
                a.setAttemptsUsed((int) s[1]);
            }));
        } catch (Exception ignored) {}
    }


    public void loadDetail() {
        if (selectedAssessmentId == null) return;
        try {
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/assessments/" + selectedAssessmentId);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                double total = 0;
                for (JsonNode q : n.path("questions")) {
                    total += q.path("points").asDouble();
                }
                assessmentDetail = new AssessmentData(
                        n.path("id").asText(),
                        n.path("title").asText(),
                        n.path("description").asText(""),
                        total,
                        n.path("courseId").asText("")
                );
                questions.clear();
                n.path("questions").forEach(q -> {
                    List<Map<String, String>> opts = new ArrayList<>();
                    q.path("options").forEach(o -> opts.add(Map.of(
                            "id",   o.path("id").asText(),
                            "text", o.path("text").asText()
                    )));
                    questions.add(new QuestionData(
                            q.path("id").asText(),
                            q.path("questionText").asText(),
                            q.path("questionType").asText(),
                            q.path("points").asDouble(),
                            opts
                    ));
                });
            }
        } catch (Exception e) {
            warn("No se pudo cargar la evaluación.");
        }
    }

    private void loadQuizResults(String assessmentId) {
        try {
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/assessments/" + assessmentId + "/submissions");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                quizResultsTitle = root.path("assessmentTitle").asText("Evaluación");
                root.path("submissions").forEach(s -> quizResults.add(new SubmissionResult(
                        s.path("userId").asText(),
                        s.path("submittedAt").asText(""),
                        s.path("score").asDouble(),
                        s.path("maxScore").asDouble(),
                        s.path("scorePct").asDouble(),
                        s.path("passed").asBoolean(),
                        s.path("attempt").asInt()
                )));
            }
        } catch (Exception e) {
            warn("No se pudieron cargar los resultados.");
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
                String redirect = "/views/assessment-take?faces-redirect=true&submissionId=" + submissionId
                        + "&assessmentId=" + selectedAssessmentId;
                if (backCourseId != null && !backCourseId.isBlank()) {
                    redirect += "&courseId=" + backCourseId;
                }
                return redirect;
            }
            if (resp.statusCode() == 403) {
                warn("Solo los estudiantes pueden tomar evaluaciones.");
                return null;
            }
            try {
                String msg = resp.body().isBlank() ? "" :
                        mapper.readTree(resp.body()).path("message").asText("");
                warn(msg.isBlank() ? "No se pudo iniciar la evaluación." : msg);
            } catch (Exception ignored) {
                warn("No se pudo iniciar la evaluación.");
            }
        } catch (Exception e) {
            warn("Error al iniciar la evaluación.");
        }
        return null;
    }

    public String submitAnswers() {
        if (submissionId == null) return null;
        try {
            ObjectNode answersMap = mapper.createObjectNode();
            selectedAnswers.forEach((qId, optId) -> {
                if (optId != null && !optId.isBlank()) {
                    answersMap.set(qId, mapper.createArrayNode().add(optId));
                }
            });
            selectedMultiAnswers.forEach((qId, optIds) -> {
                if (optIds != null && optIds.length > 0) {
                    ArrayNode arr = mapper.createArrayNode();
                    for (String optId : optIds) arr.add(optId);
                    answersMap.set(qId, arr);
                }
            });

            ObjectNode bodyNode = mapper.createObjectNode();
            bodyNode.set("answers", answersMap);
            bodyNode.put("durationSeconds", 0L);
            String body = bodyNode.toString();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ASSESSMENT_URL + "/api/v1/submissions/" + submissionId + "/submit"))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode result = mapper.readTree(resp.body());
                double scorePct = result.path("scorePct").asDouble();
                long scoreRounded = Math.round(scorePct);
                this.submissionResult = Map.of(
                        "score",    scoreRounded,
                        "passed",   result.path("passed").asBoolean(),
                        "status",   result.path("status").asText("GRADED")
                );
                JsonNode rec = result.path("recommendation");
                if (!rec.isNull() && !rec.isMissingNode()) {
                    adaptiveRecommendation = rec.path("message").asText("");
                    String sid = rec.path("supplementaryLessonId").asText(null);
                    supplementaryLessonId  = (sid != null && !sid.isBlank()) ? sid : null;
                    if (supplementaryLessonId != null) loadSupplementaryLesson(supplementaryLessonId);
                } else {
                    adaptiveRecommendation = "";
                    supplementaryLessonId  = null;
                }
                return "/views/assessment-result";
            }
            warn("Error al enviar respuestas.");
        } catch (Exception e) {
            warn("Error al enviar: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " — " + e.getMessage() : ""));
        }
        return null;
    }

    private void loadSupplementaryLesson(String lessonId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(COURSE_URL + "/api/v1/lessons/" + lessonId))
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            System.err.println("[AssessmentBean] loadSupplementaryLesson HTTP " + resp.statusCode() + " lessonId=" + lessonId);
            if (resp.statusCode() == 200) {
                JsonNode n = mapper.readTree(resp.body());
                supplementaryLesson = new HashMap<>();
                supplementaryLesson.put("id",    n.path("id").asText());
                supplementaryLesson.put("title", n.path("title").asText());
            }
        } catch (Exception e) {
            System.err.println("[AssessmentBean] loadSupplementaryLesson error: " + e);
        }
    }

    public String resolveStudentName(String userId) {
        if (userId == null || userId.isBlank()) return "Desconocido";
        return authorNameCache.computeIfAbsent(userId, id -> {
            try {
                HttpResponse<String> resp = http.send(bearer(USER_URL + "/api/v1/users/" + id + "/name"),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return mapper.readTree(resp.body()).path("displayName").asText(id);
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

    private void warn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }

    // ---- accessors ----
    public List<CourseGroup>         getGroups()              { return groups; }
    public boolean                   hasGroups()              { return !groups.isEmpty(); }
    public AssessmentData            getAssessmentDetail()    { return assessmentDetail; }
    public List<QuestionData>        getQuestions()           { return questions; }
    public Map<String, Object>       getSubmissionResult()    { return submissionResult; }
    public String getAdaptiveRecommendation()                 { return adaptiveRecommendation; }
    public String getSelectedAssessmentId()                   { return selectedAssessmentId; }
    public void   setSelectedAssessmentId(String id)         { this.selectedAssessmentId = id; }
    public String getSubmissionId()                           { return submissionId; }
    public void   setSubmissionId(String id)                  { this.submissionId = id; }
    public Map<String, String>   getSelectedAnswers()     { return selectedAnswers; }
    public Map<String, String[]> getSelectedMultiAnswers(){ return selectedMultiAnswers; }
    public String                    getSupplementaryLessonId(){ return supplementaryLessonId; }
    public Map<String, Object>       getSupplementaryLesson() { return supplementaryLesson; }
    public String            getQuizResultsTitle()            { return quizResultsTitle; }
    public List<SubmissionResult> getQuizResults()            { return quizResults; }
    public String getSelectedAssessmentForResults()           { return selectedAssessmentForResults; }
    public void   setSelectedAssessmentForResults(String id)  { this.selectedAssessmentForResults = id; }
    public boolean hasQuizResults()                           { return !quizResults.isEmpty(); }
    public String getFilteredCourseId()                       { return filteredCourseId; }
    public String getBackCourseId()                           { return backCourseId; }
    public void   setBackCourseId(String id)                  { this.backCourseId = id; }
}
