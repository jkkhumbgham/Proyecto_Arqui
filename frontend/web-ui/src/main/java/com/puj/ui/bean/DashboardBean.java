package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.http.HttpResponse;
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

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    // DIRECTOR / ADMIN
    private long   totalUsers;
    private long   totalEnrollments;
    private long   totalCourses;
    private double avgScore;
    private double overallPassRate;
    private List<Map<String, Object>> topCourses    = new ArrayList<>();
    private List<Map<String, Object>> inactiveUsers = new ArrayList<>();

    public static class CourseStatRow {
        private final String courseId, courseTitle;
        private final long   count;
        public CourseStatRow(String courseId, String courseTitle, long count) {
            this.courseId = courseId; this.courseTitle = courseTitle; this.count = count;
        }
        public String getCourseId()    { return courseId; }
        public String getCourseTitle() { return courseTitle; }
        public long   getCount()       { return count; }
    }
    private long                totalCompletedEnrollments = 0;
    private List<CourseStatRow> popularCourseStats        = new ArrayList<>();
    private List<CourseStatRow> completedCourseStats      = new ArrayList<>();

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
        public String  getCourseId()                 { return courseId; }
        public String  getTitle()                    { return title; }
        public String  getStatus()                   { return status; }
        public double  getProgressPct()              { return progressPct; }
        public int     getCompletedCount()           { return completedCount; }
        public int     getTotalLessons()             { return totalLessons; }
        public String  getFirstUncompletedLessonId() { return firstUncompletedLessonId; }
        public void    setFirstUncompletedLessonId(String id) { this.firstUncompletedLessonId = id; }
        public boolean isCompleted()                 { return "COMPLETED".equals(status); }
    }

    public static class EvalGrade {
        private final String  assessmentTitle;
        private final double  scorePct;
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
        public String          getCourseId()    { return courseId; }
        public String          getCourseTitle() { return courseTitle; }
        public double          getAvgScorePct() { return avgScorePct; }
        public List<EvalGrade> getEvals()       { return evals; }
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

    private List<EnrolledCourse>         enrolledCourses        = new ArrayList<>();
    private List<MyCourse>               instructorCourses      = new ArrayList<>();
    private List<StudentCourseStats>     studentGrades          = new ArrayList<>();
    private List<InstructorCourseMetric> instructorMetrics      = new ArrayList<>();
    private long                         uniqueInstructorStudents = 0;

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        if (session.hasRole("DIRECTOR", "ADMIN")) {
            loadSummary();
            loadTopCourses();
            loadCourseStats();
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
            HttpResponse<String> resp = api.get(
                    ANALYTICS_URL + "/api/v1/analytics/dashboard/summary", session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
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
            HttpResponse<String> resp = api.get(
                    ANALYTICS_URL + "/api/v1/analytics/dashboard/top-courses", session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body()).forEach(n -> {
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
            HttpResponse<String> resp = api.get(
                    ANALYTICS_URL + "/api/v1/analytics/dashboard/inactive-users?days=30&size=50",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body()).forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("email",       n.path("email").asText());
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

    private void loadCourseStats() {
        try {
            HttpResponse<String> resp = api.get(
                    COURSE_URL + "/api/v1/enrollments/admin/course-stats?limit=5",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode root = api.readTree(resp.body());
                totalCompletedEnrollments = root.path("totalCompletedEnrollments").asLong(0);
                root.path("popularCourses").forEach(n -> popularCourseStats.add(
                        new CourseStatRow(n.path("courseId").asText(),
                                n.path("courseTitle").asText(), n.path("enrollCount").asLong(0))));
                root.path("completedCourses").forEach(n -> completedCourseStats.add(
                        new CourseStatRow(n.path("courseId").asText(),
                                n.path("courseTitle").asText(), n.path("completedCount").asLong(0))));
            }
        } catch (Exception ignored) {}
    }

    private void loadEnrolledCourses() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/enrollments/my",
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;

            JsonNode enrollments = api.readTree(resp.body());
            enrollments.forEach(n -> {
                String cid    = n.path("courseId").asText();
                double pct    = n.path("progressPct").asDouble();
                String status = n.path("status").asText("ACTIVE");

                int completedCount = 0, totalLessons = 0;
                Set<String> completedIds = new HashSet<>();
                try {
                    HttpResponse<String> pResp = api.get(
                            COURSE_URL + "/api/v1/courses/" + cid + "/progress",
                            session.getAccessToken());
                    if (pResp.statusCode() == 200) {
                        JsonNode p = api.readTree(pResp.body());
                        completedCount = (int) p.path("completedCount").asLong();
                        totalLessons   = (int) p.path("totalLessons").asLong();
                        pct            = p.path("progressPct").asDouble();
                        p.path("completedLessonIds").forEach(id -> completedIds.add(id.asText()));
                    }
                } catch (Exception ignored2) {}

                EnrolledCourse ec = new EnrolledCourse(
                        cid, n.path("courseTitle").asText(), status, pct, completedCount, totalLessons);

                try {
                    HttpResponse<String> cResp = api.get(
                            COURSE_URL + "/api/v1/courses/" + cid, session.getAccessToken());
                    if (cResp.statusCode() == 200) {
                        JsonNode course = api.readTree(cResp.body());
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
            FacesMessageUtil.warn("No se pudo cargar tus cursos.");
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
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/submissions/my?size=200", session.getAccessToken());
            if (resp.statusCode() != 200) return;

            JsonNode arr = api.readTree(resp.body());
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

            enrolledCourses.forEach(ec -> courseTitles.put(ec.getCourseId(), ec.getTitle()));

            best.forEach((cid, assessments) -> {
                List<EvalGrade> grades = new ArrayList<>();
                double          total  = 0;
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
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/courses/my",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode root = api.readTree(resp.body());
                JsonNode arr  = root.isArray() ? root : root.path("data");
                arr.forEach(n -> instructorCourses.add(new MyCourse(
                        n.path("id").asText(), n.path("title").asText(),
                        n.path("status").asText(), n.path("maxStudents").asInt()
                )));
            }
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar tus cursos.");
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
                HttpResponse<String> resp = api.get(
                        ANALYTICS_URL + "/api/v1/analytics/courses/" + cid + "/summary",
                        session.getAccessToken());
                if (resp.statusCode() == 200) {
                    avgGrade = api.readTree(resp.body()).path("averageScore").asDouble();
                }
            } catch (Exception ignored) {}

            try {
                HttpResponse<String> resp = api.get(
                        COURSE_URL + "/api/v1/enrollments/course/" + cid + "/stats",
                        session.getAccessToken());
                if (resp.statusCode() == 200) {
                    JsonNode n = api.readTree(resp.body());
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

        if (!instructorCourses.isEmpty()) {
            String ids = instructorCourses.stream()
                    .map(MyCourse::getId)
                    .collect(java.util.stream.Collectors.joining(","));
            try {
                HttpResponse<String> resp = api.get(
                        COURSE_URL + "/api/v1/enrollments/unique-students?courseIds=" + ids,
                        session.getAccessToken());
                if (resp.statusCode() == 200) {
                    uniqueInstructorStudents = api.readTree(resp.body()).path("uniqueStudents").asLong();
                }
            } catch (Exception ignored) {}
        }
    }

    public List<Map<String, Object>> getMyCourses() {
        List<Map<String, Object>> list = new ArrayList<>();
        enrolledCourses.forEach(ec -> list.add(Map.of(
                "courseId", ec.getCourseId(), "title", ec.getTitle(),
                "progress", (int) ec.getProgressPct(), "status", ec.getStatus()
        )));
        return list;
    }

    public long getTotalInstructorStudents() { return uniqueInstructorStudents; }

    public long getActiveInstructorCourseCount() {
        return instructorCourses.stream().filter(c -> "PUBLISHED".equals(c.getStatus())).count();
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
    public List<Map<String, Object>>    getTopCourses()               { return topCourses; }
    public List<Map<String, Object>>    getInactiveUsers()            { return inactiveUsers; }
    public long                getTotalCompletedEnrollments()         { return totalCompletedEnrollments; }
    public List<CourseStatRow> getPopularCourseStats()                { return popularCourseStats; }
    public List<CourseStatRow> getCompletedCourseStats()              { return completedCourseStats; }
    public List<EnrolledCourse>         getEnrolledCourses()          { return enrolledCourses; }
    public List<MyCourse>               getInstructorCourses()        { return instructorCourses; }
    public List<StudentCourseStats>     getStudentGrades()            { return studentGrades; }
    public List<InstructorCourseMetric> getInstructorMetrics()        { return instructorMetrics; }
}
