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
import java.util.*;

@Named
@RequestScoped
public class DashboardBean {

    private static final String USER_URL =
            System.getenv().getOrDefault("USER_SERVICE_URL", "http://user-service:8080");
    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");
    private static final String ANALYTICS_URL =
            System.getenv().getOrDefault("ANALYTICS_SERVICE_URL", "http://analytics-service:8080");

    @Inject private SessionBean session;

    private final HttpClient   http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    // DIRECTOR / ADMIN
    private long   totalUsers;
    private long   totalEnrollments;
    private long   totalCourses;
    private double avgScore;
    private double overallPassRate;
    private List<Map<String, Object>> topCourses    = new ArrayList<>();
    private List<Map<String, Object>> inactiveUsers = new ArrayList<>();

    // STUDENT / INSTRUCTOR
    public static class EnrolledCourse {
        private final String courseId, title, status;
        private final double progressPct;
        private final int    completedCount, totalLessons;
        private String firstUncompletedLessonId;

        public EnrolledCourse(String courseId, String title, String status,
                               double progressPct, int completedCount, int totalLessons) {
            this.courseId       = courseId;    this.title    = title;
            this.status         = status;      this.progressPct    = progressPct;
            this.completedCount = completedCount; this.totalLessons = totalLessons;
        }
        public String getCourseId()              { return courseId; }
        public String getTitle()                 { return title; }
        public String getStatus()                { return status; }
        public double getProgressPct()           { return progressPct; }
        public int    getCompletedCount()        { return completedCount; }
        public int    getTotalLessons()          { return totalLessons; }
        public String getFirstUncompletedLessonId() { return firstUncompletedLessonId; }
        public void   setFirstUncompletedLessonId(String id) { this.firstUncompletedLessonId = id; }
        public boolean isCompleted()             { return "COMPLETED".equals(status); }
    }

    public static class EvalGrade {
        private final String assessmentTitle;
        private final double scorePct;
        private final boolean passed;
        public EvalGrade(String assessmentTitle, double scorePct, boolean passed) {
            this.assessmentTitle = assessmentTitle;
            this.scorePct        = scorePct;
            this.passed          = passed;
        }
        public String  getAssessmentTitle() { return assessmentTitle; }
        public double  getScorePct()        { return scorePct; }
        public boolean isPassed()           { return passed; }
    }

    public static class StudentCourseStats {
        private final String courseId, courseTitle;
        private final double avgScorePct;
        private final List<EvalGrade> evals;
        public StudentCourseStats(String courseId, String courseTitle,
                                   double avgScorePct, List<EvalGrade> evals) {
            this.courseId    = courseId;
            this.courseTitle = courseTitle;
            this.avgScorePct = avgScorePct;
            this.evals       = evals;
        }
        public String         getCourseId()    { return courseId; }
        public String         getCourseTitle() { return courseTitle; }
        public double         getAvgScorePct() { return avgScorePct; }
        public List<EvalGrade> getEvals()      { return evals; }
    }

    public static class InstructorCourseMetric {
        private final String courseId, courseTitle;
        private final double avgGradePct, avgProgressPct;
        private final long   enrolledCount;
        private final List<Map<String, Object>> moduleDistribution;
        public InstructorCourseMetric(String courseId, String courseTitle,
                                       double avgGradePct, long enrolledCount,
                                       double avgProgressPct, List<Map<String, Object>> moduleDistribution) {
            this.courseId           = courseId;
            this.courseTitle        = courseTitle;
            this.avgGradePct        = avgGradePct;
            this.enrolledCount      = enrolledCount;
            this.avgProgressPct     = avgProgressPct;
            this.moduleDistribution = moduleDistribution;
        }
        public String   getCourseId()            { return courseId; }
        public String   getCourseTitle()         { return courseTitle; }
        public double   getAvgGradePct()         { return avgGradePct; }
        public long     getEnrolledCount()       { return enrolledCount; }
        public double   getAvgProgressPct()      { return avgProgressPct; }
        public List<Map<String, Object>> getModuleDistribution() { return moduleDistribution; }
    }

    // Instructor: their own courses (kept for backwards compat)
    public static class MyCourse {
        private final String id, title, status;
        private final int    maxStudents;
        public MyCourse(String id, String title, String status, int maxStudents) {
            this.id = id; this.title = title; this.status = status; this.maxStudents = maxStudents;
        }
        public String getId()          { return id; }
        public String getTitle()       { return title; }
        public String getStatus()      { return status; }
        public int    getMaxStudents() { return maxStudents; }
    }

    private List<EnrolledCourse>        enrolledCourses       = new ArrayList<>();
    private List<MyCourse>              instructorCourses     = new ArrayList<>();
    private List<StudentCourseStats>    studentGrades         = new ArrayList<>();
    private List<InstructorCourseMetric> instructorMetrics   = new ArrayList<>();
    private long                        uniqueInstructorStudents = 0;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        if (session.hasRole("DIRECTOR", "ADMIN")) {
            loadSummary();
            loadTopCourses();
            loadInactiveUsers();
        } else if (session.hasRole("STUDENT")) {
            loadEnrolledCourses();
            loadStudentGrades();
        } else if (session.hasRole("INSTRUCTOR")) {
            loadInstructorCourses();
            loadInstructorMetrics();
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
                totalCourses     = n.path("totalCourses").asLong(0);
                overallPassRate  = n.path("passRate").asDouble(0);
            }
        } catch (Exception ignored) {}
    }

    private void loadTopCourses() {
        try {
            HttpRequest req = bearer(ANALYTICS_URL + "/api/v1/analytics/dashboard/top-courses");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title",       n.path("courseTitle").asText());
                    m.put("enrollments", n.path("totalEnrollments").asLong());
                    m.put("avgScore",    n.path("averageScore").asDouble());
                    topCourses.add(m);
                });
            }
        } catch (Exception ignored) {}
    }

    private void loadInactiveUsers() {
        try {
            // Datos vienen de analytics-service (alimentado por eventos RabbitMQ),
            // no de user-service directamente.
            HttpRequest req = bearer(ANALYTICS_URL + "/api/v1/analytics/dashboard/inactive-users?days=30&size=50");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode arr = mapper.readTree(resp.body());
                arr.forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("email",       n.path("email").asText());
                    // studentName puede ser "FirstName LastName" o solo nombre
                    String fullName = n.path("studentName").asText("");
                    String[] parts  = fullName.split(" ", 2);
                    m.put("firstName",   parts.length > 0 ? parts[0] : "");
                    m.put("lastName",    parts.length > 1 ? parts[1] : "");
                    m.put("role",        n.path("role").asText());
                    m.put("lastLoginAt", n.path("lastLoginAt").asText("Nunca"));
                    inactiveUsers.add(m);
                });
            }
        } catch (Exception ignored) {}
    }

    private void loadEnrolledCourses() {
        try {
            HttpRequest req = bearer(COURSE_URL + "/api/v1/enrollments/my");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            JsonNode enrollments = mapper.readTree(resp.body());
            enrollments.forEach(n -> {
                String cid    = n.path("courseId").asText();
                double pct    = n.path("progressPct").asDouble();
                String status = n.path("status").asText("ACTIVE");

                int completedCount = 0, totalLessons = 0;
                Set<String> completedIds = new HashSet<>();
                try {
                    HttpRequest pReq = bearer(COURSE_URL + "/api/v1/courses/" + cid + "/progress");
                    HttpResponse<String> pResp = http.send(pReq, HttpResponse.BodyHandlers.ofString());
                    if (pResp.statusCode() == 200) {
                        JsonNode p = mapper.readTree(pResp.body());
                        completedCount = (int) p.path("completedCount").asLong();
                        totalLessons   = (int) p.path("totalLessons").asLong();
                        pct            = p.path("progressPct").asDouble();
                        p.path("completedLessonIds").forEach(id -> completedIds.add(id.asText()));
                    }
                } catch (Exception ignored2) {}

                EnrolledCourse ec = new EnrolledCourse(
                        cid, n.path("courseTitle").asText(), status, pct, completedCount, totalLessons);

                try {
                    HttpRequest cReq = bearer(COURSE_URL + "/api/v1/courses/" + cid);
                    HttpResponse<String> cResp = http.send(cReq, HttpResponse.BodyHandlers.ofString());
                    if (cResp.statusCode() == 200) {
                        JsonNode course = mapper.readTree(cResp.body());
                        outer:
                        for (JsonNode m : course.path("modules")) {
                            for (JsonNode l : m.path("lessons")) {
                                String lid = l.path("id").asText();
                                if (!completedIds.contains(lid)) {
                                    ec.setFirstUncompletedLessonId(lid);
                                    break outer;
                                }
                            }
                        }
                    }
                } catch (Exception ignored3) {}

                enrolledCourses.add(ec);
            });
        } catch (Exception e) {
            warn("No se pudo cargar tus cursos.");
        }
    }

    private static class BestAttempt {
        String title; double scorePct; boolean passed;
        BestAttempt(String title, double scorePct, boolean passed) {
            this.title = title; this.scorePct = scorePct; this.passed = passed;
        }
    }

    private void loadStudentGrades() {
        try {
            HttpRequest req = bearer(ASSESSMENT_URL + "/api/v1/submissions/my?size=200");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            JsonNode arr = mapper.readTree(resp.body());
            // Group by courseId; keep best (highest scorePct) attempt per assessmentId
            Map<String, String>                  courseTitles = new LinkedHashMap<>();
            Map<String, Map<String, BestAttempt>> best        = new LinkedHashMap<>();

            arr.forEach(s -> {
                String cid    = s.path("courseId").asText();
                String aid    = s.path("assessmentId").asText();
                String atitle = s.path("assessmentTitle").asText();
                double pct    = s.path("scorePct").asDouble();
                boolean pass  = s.path("passed").asBoolean();

                courseTitles.putIfAbsent(cid, cid);
                best.computeIfAbsent(cid, k -> new LinkedHashMap<>());
                BestAttempt cur = best.get(cid).get(aid);
                if (cur == null || pct > cur.scorePct) {
                    best.get(cid).put(aid, new BestAttempt(atitle, pct, pass));
                }
            });

            // Enrich course titles from already-loaded enrolledCourses
            enrolledCourses.forEach(ec -> courseTitles.put(ec.getCourseId(), ec.getTitle()));

            best.forEach((cid, assessments) -> {
                List<EvalGrade> grades  = new ArrayList<>();
                double          total   = 0;
                for (BestAttempt ba : assessments.values()) {
                    grades.add(new EvalGrade(ba.title, ba.scorePct, ba.passed));
                    total += ba.scorePct;
                }
                double avg = assessments.isEmpty() ? 0 : total / assessments.size();
                studentGrades.add(new StudentCourseStats(
                        cid, courseTitles.getOrDefault(cid, cid), avg, grades));
            });
        } catch (Exception ignored) {}
    }

    private void loadInstructorCourses() {
        try {
            HttpRequest req = bearer(COURSE_URL + "/api/v1/courses/my");
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode arr  = root.isArray() ? root : root.path("data");
                arr.forEach(n -> instructorCourses.add(new MyCourse(
                        n.path("id").asText(), n.path("title").asText(),
                        n.path("status").asText(), n.path("maxStudents").asInt()
                )));
            }
        } catch (Exception e) {
            warn("No se pudo cargar tus cursos.");
        }
    }

    private void loadInstructorMetrics() {
        for (MyCourse mc : instructorCourses) {
            String cid = mc.getId();
            double avgGrade    = 0;
            long   enrolled    = 0;
            double avgProgress = 0;
            List<Map<String, Object>> modules = new ArrayList<>();

            try {
                HttpRequest req = bearer(ANALYTICS_URL + "/api/v1/analytics/courses/" + cid + "/summary");
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode n = mapper.readTree(resp.body());
                    avgGrade = n.path("averageScore").asDouble();
                }
            } catch (Exception ignored) {}

            try {
                HttpRequest req = bearer(COURSE_URL + "/api/v1/enrollments/course/" + cid + "/stats");
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode n = mapper.readTree(resp.body());
                    enrolled    = n.path("enrolledCount").asLong();
                    avgProgress = n.path("avgProgressPct").asDouble();
                    n.path("moduleDistribution").forEach(m -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("moduleTitle",  m.path("moduleTitle").asText());
                        row.put("studentCount", m.path("studentCount").asLong());
                        modules.add(row);
                    });
                }
            } catch (Exception ignored) {}

            instructorMetrics.add(new InstructorCourseMetric(
                    cid, mc.getTitle(), avgGrade, enrolled, avgProgress, modules));
        }

        // Unique students across all instructor courses (deduplicated)
        if (!instructorCourses.isEmpty()) {
            String ids = instructorCourses.stream()
                    .map(MyCourse::getId)
                    .collect(java.util.stream.Collectors.joining(","));
            try {
                HttpRequest req = bearer(COURSE_URL + "/api/v1/enrollments/unique-students?courseIds=" + ids);
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    uniqueInstructorStudents = mapper.readTree(resp.body()).path("uniqueStudents").asLong();
                }
            } catch (Exception ignored) {}
        }
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

    public List<Map<String, Object>> getMyCourses() {
        List<Map<String, Object>> list = new ArrayList<>();
        enrolledCourses.forEach(ec -> list.add(Map.of(
                "courseId", ec.getCourseId(), "title", ec.getTitle(),
                "progress", (int) ec.getProgressPct(), "status", ec.getStatus()
        )));
        return list;
    }

    // Aggregate stats for instructor dashboard
    public long getTotalInstructorStudents() {
        return uniqueInstructorStudents;
    }

    /** Cursos del instructor con status PUBLISHED (los que realmente están activos). */
    public long getActiveInstructorCourseCount() {
        return instructorCourses.stream()
                .filter(c -> "PUBLISHED".equals(c.getStatus()))
                .count();
    }
    public double getAvgInstructorGrade() {
        return instructorMetrics.isEmpty() ? 0 :
                instructorMetrics.stream().mapToDouble(InstructorCourseMetric::getAvgGradePct)
                        .average().orElse(0);
    }

    public long   getTotalUsers()       { return totalUsers; }
    public long   getTotalEnrollments() { return totalEnrollments; }
    public long   getTotalCourses()     { return totalCourses; }
    public double getAvgScore()         { return avgScore; }
    public double getOverallPassRate()  { return overallPassRate; }
    public List<Map<String, Object>>    getTopCourses()        { return topCourses; }
    public List<Map<String, Object>>    getInactiveUsers()     { return inactiveUsers; }
    public List<EnrolledCourse>         getEnrolledCourses()   { return enrolledCourses; }
    public List<MyCourse>               getInstructorCourses() { return instructorCourses; }
    public List<StudentCourseStats>     getStudentGrades()     { return studentGrades; }
    public List<InstructorCourseMetric> getInstructorMetrics() { return instructorMetrics; }
}
