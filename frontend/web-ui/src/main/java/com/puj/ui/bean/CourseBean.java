package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@Named
@ViewScoped
public class CourseBean implements Serializable {

    private static final long   serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(CourseBean.class.getName());

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    // ---- typed data holders ----
    public static class CourseData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, title, description, status;
        private final int maxStudents;
        public CourseData(String id, String title, String description, String status, int maxStudents) {
            this.id = id; this.title = title; this.description = description;
            this.status = status; this.maxStudents = maxStudents;
        }
        public String getId()          { return id; }
        public String getTitle()       { return title; }
        public String getDescription() { return description; }
        public String getStatus()      { return status; }
        public int    getMaxStudents() { return maxStudents; }
    }

    public static class LessonData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String  id, title, content, assessmentId, assessmentTitle;
        private final int     orderIndex;
        private final Integer durationMinutes;
        private final boolean supplementary;
        public LessonData(String id, String title, String content, int orderIndex,
                          Integer durationMinutes, String assessmentId, String assessmentTitle,
                          boolean supplementary) {
            this.id = id; this.title = title; this.content = content;
            this.orderIndex = orderIndex; this.durationMinutes = durationMinutes;
            this.assessmentId = assessmentId; this.assessmentTitle = assessmentTitle;
            this.supplementary = supplementary;
        }
        public String  getId()               { return id; }
        public String  getTitle()            { return title; }
        public String  getContent()          { return content; }
        public int     getOrderIndex()       { return orderIndex; }
        public Integer getDurationMinutes()  { return durationMinutes; }
        public String  getAssessmentId()     { return assessmentId; }
        public String  getAssessmentTitle()  { return assessmentTitle; }
        public boolean isSupplementary()     { return supplementary; }
    }

    public static class AssessmentData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, title, lessonId;
        private final double passingScorePct;
        public AssessmentData(String id, String title, String lessonId, double passingScorePct) {
            this.id = id; this.title = title; this.lessonId = lessonId; this.passingScorePct = passingScorePct;
        }
        public String  getId()              { return id; }
        public String  getTitle()           { return title; }
        public String  getLessonId()        { return lessonId; }
        public double  getPassingScorePct() { return passingScorePct; }
        public boolean isCourseLevelOnly()  { return lessonId == null || lessonId.isBlank(); }
    }

    public static class ModuleData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String          id, title, description;
        private final int             orderIndex;
        private final List<LessonData> lessons;
        public ModuleData(String id, String title, String description, int orderIndex,
                          List<LessonData> lessons) {
            this.id = id; this.title = title; this.description = description;
            this.orderIndex = orderIndex; this.lessons = lessons;
        }
        public String           getId()          { return id; }
        public String           getTitle()       { return title; }
        public String           getDescription() { return description; }
        public int              getOrderIndex()  { return orderIndex; }
        public List<LessonData> getLessons()     { return lessons; }
    }

    private List<CourseData>     courses           = new ArrayList<>();
    private CourseData           courseDetail;
    private List<ModuleData>     modules           = new ArrayList<>();
    private List<AssessmentData> courseAssessments = new ArrayList<>();
    private Set<String>          enrolledCourseIds = new HashSet<>();
    private Set<String>          lockedModuleIds   = new HashSet<>();
    private Set<String>          unlockedSupplementaryLessonIds = new HashSet<>();
    private Set<String>          completedLessonIdsSet          = new HashSet<>();

    private String  selectedCourseId;
    private double  progressPct;
    private int     completedLessons;
    private int     totalLessons;
    private String  firstUncompletedLessonId;
    private boolean courseFinishable = false;

    private String newTitle;
    private String newDescription;
    private int    newMaxStudents = 30;
    private String newStatus      = "DRAFT";

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;

        String courseIdParam = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("courseId");
        LOG.fine("[CourseBean] @PostConstruct courseIdParam=" + courseIdParam);
        if (courseIdParam != null && !courseIdParam.isBlank()) {
            selectedCourseId = courseIdParam;
            if (session.hasRole("STUDENT")) {
                loadEnrolledIds();
                loadStudentProgress(courseIdParam);
            }
            loadDetail();
            if (session.hasRole("STUDENT")) {
                resolveFirstUncompletedLesson();
                computeModuleLocks();
                loadUnlockedSupplementaryLessons(courseIdParam);
                computeCourseFinishable();
            }
            return;
        }

        if (session.hasRole("INSTRUCTOR", "ADMIN", "DIRECTOR")) {
            loadMyCourses();
        } else {
            loadPublicCourses();
        }

        if (session.hasRole("STUDENT")) {
            loadEnrolledIds();
        }
    }

    private void loadPublicCourses() {
        try {
            populateCourses(api.getPublic(COURSE_URL + "/api/v1/courses"));
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar el catálogo de cursos.");
        }
    }

    private void loadMyCourses() {
        try {
            populateCourses(api.get(COURSE_URL + "/api/v1/courses/my", session.getAccessToken()));
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar tus cursos.");
        }
    }

    private void populateCourses(HttpResponse<String> resp) throws Exception {
        if (resp.statusCode() == 200) {
            JsonNode root = api.readTree(resp.body());
            JsonNode arr  = root.isArray() ? root : root.path("data");
            arr.forEach(n -> courses.add(new CourseData(
                    n.path("id").asText(),
                    n.path("title").asText(),
                    n.path("description").asText(""),
                    n.path("status").asText(),
                    n.path("maxStudents").asInt()
            )));
        }
    }

    private void loadStudentProgress(String courseId) {
        try {
            HttpResponse<String> resp = api.get(
                    COURSE_URL + "/api/v1/courses/" + courseId + "/progress",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                completedLessons = (int) n.path("completedCount").asLong();
                totalLessons     = (int) n.path("totalLessons").asLong();
                progressPct      = n.path("progressPct").asDouble();
                n.path("completedLessonIds").forEach(id -> completedLessonIdsSet.add(id.asText()));
            }
        } catch (Exception ignored) {}
    }

    private void resolveFirstUncompletedLesson() {
        outer:
        for (ModuleData m : modules) {
            for (LessonData l : m.getLessons()) {
                if (l.isSupplementary()) continue;
                if (!completedLessonIdsSet.contains(l.getId())) {
                    firstUncompletedLessonId = l.getId();
                    break outer;
                }
            }
        }
    }

    private void computeModuleLocks() {
        if (modules.size() <= 1 || session.getAccessToken() == null) return;
        String userId = session.getUserId();
        if (userId == null || userId.isBlank()) return;

        Map<String, List<String>> moduleAssessments = new HashMap<>();
        for (ModuleData mod : modules) {
            Set<String> moduleLessonIds = new HashSet<>();
            mod.getLessons().forEach(l -> moduleLessonIds.add(l.getId()));
            List<String> aids = new ArrayList<>();
            courseAssessments.forEach(a -> {
                if (a.getLessonId() != null && moduleLessonIds.contains(a.getLessonId())) {
                    aids.add(a.getId());
                }
            });
            moduleAssessments.put(mod.getId(), aids);
        }

        for (int i = 1; i < modules.size(); i++) {
            ModuleData prev     = modules.get(i - 1);
            ModuleData curr     = modules.get(i);
            List<String> prevAids = moduleAssessments.get(prev.getId());
            if (prevAids == null || prevAids.isEmpty()) continue;

            String idsParam = String.join(",", prevAids);
            try {
                String url = ASSESSMENT_URL + "/api/v1/submissions/avg-for-assessments"
                        + "?userId=" + userId + "&assessmentIds=" + idsParam;
                HttpResponse<String> resp = api.get(url, session.getAccessToken());
                if (resp.statusCode() == 200) {
                    double avg = api.readTree(resp.body()).path("avgScorePct").asDouble(0);
                    if (avg < 60.0) lockedModuleIds.add(curr.getId());
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean isModuleLocked(String moduleId) {
        return lockedModuleIds.contains(moduleId);
    }

    public boolean isLessonAccessible(String lessonId) {
        if (!session.hasRole("STUDENT")) return true;
        return completedLessonIdsSet.contains(lessonId)
               || lessonId.equals(firstUncompletedLessonId)
               || unlockedSupplementaryLessonIds.contains(lessonId);
    }

    private void computeCourseFinishable() {
        if (progressPct < 100.0) return;
        String userId = session.getUserId();
        if (userId == null || userId.isBlank()) return;
        if (courseAssessments.isEmpty()) { courseFinishable = true; return; }
        courseFinishable = true;
        for (AssessmentData a : courseAssessments) {
            try {
                String url = ASSESSMENT_URL + "/api/v1/submissions/avg-for-assessments"
                        + "?userId=" + userId + "&assessmentIds=" + a.getId();
                HttpResponse<String> resp = api.get(url, session.getAccessToken());
                if (resp.statusCode() != 200) { courseFinishable = false; return; }
                double avg = api.readTree(resp.body()).path("avgScorePct").asDouble(0);
                if (avg < 60.0) { courseFinishable = false; return; }
            } catch (Exception ignored) { courseFinishable = false; return; }
        }
    }

    public boolean isCourseFinishable() { return courseFinishable; }

    public String finalizeCourse() {
        try {
            HttpResponse<String> resp = api.postEmpty(
                    COURSE_URL + "/api/v1/enrollments/courses/" + selectedCourseId + "/finalize",
                    session.getAccessToken());
            if (resp.statusCode() == 200) return "dashboard?faces-redirect=true";
            FacesMessageUtil.warn("No se pudo finalizar el curso.");
        } catch (Exception e) { FacesMessageUtil.warn("Error al finalizar el curso."); }
        return null;
    }

    private void loadEnrolledIds() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/enrollments/my",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body()).forEach(n -> enrolledCourseIds.add(n.path("courseId").asText()));
            }
        } catch (Exception ignored) {}
    }

    public void loadDetail() {
        if (selectedCourseId == null || selectedCourseId.isBlank()) return;
        try {
            HttpResponse<String> resp = api.getWithOptionalAuth(
                    COURSE_URL + "/api/v1/courses/" + selectedCourseId, session.getAccessToken());
            if (resp.statusCode() != 200) return;

            JsonNode n = api.readTree(resp.body());
            courseDetail = new CourseData(
                    n.path("id").asText(),
                    n.path("title").asText(),
                    n.path("description").asText(""),
                    n.path("status").asText(),
                    n.path("maxStudents").asInt()
            );

            Map<String, AssessmentData> lessonToAssessment = loadAssessmentMapForCourse(selectedCourseId);

            modules.clear();
            n.path("modules").forEach(m -> {
                List<LessonData> lessons = new ArrayList<>();
                m.path("lessons").forEach(l -> {
                    String lessonId = l.path("id").asText();
                    AssessmentData ad = lessonToAssessment.get(lessonId);
                    lessons.add(new LessonData(
                            lessonId,
                            l.path("title").asText(),
                            l.path("content").asText(""),
                            l.path("orderIndex").asInt(),
                            l.path("durationMinutes").isNull() || l.path("durationMinutes").isMissingNode()
                                    ? null : l.path("durationMinutes").asInt(),
                            ad != null ? ad.getId()    : null,
                            ad != null ? ad.getTitle() : null,
                            l.path("supplementary").asBoolean(false)
                    ));
                });
                modules.add(new ModuleData(
                        m.path("id").asText(),
                        m.path("title").asText(),
                        m.path("description").asText(""),
                        m.path("orderIndex").asInt(),
                        lessons
                ));
            });
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar el detalle del curso.");
        }
    }

    private Map<String, AssessmentData> loadAssessmentMapForCourse(String courseId) {
        Map<String, AssessmentData> map = new HashMap<>();
        courseAssessments.clear();
        if (session.getAccessToken() == null) return map;
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/assessments/course/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body()).forEach(a -> {
                    String lessonId = a.path("lessonId").isMissingNode() || a.path("lessonId").isNull()
                            ? null : a.path("lessonId").asText(null);
                    String aId   = a.path("id").asText();
                    String title = a.path("title").asText("");
                    double pct   = a.path("passingScorePct").asDouble(60.0);
                    if (!aId.isBlank()) {
                        AssessmentData ad = new AssessmentData(aId, title, lessonId, pct);
                        courseAssessments.add(ad);
                        if (lessonId != null && !lessonId.isBlank()) {
                            map.put(lessonId, ad);
                        }
                    }
                });
            }
        } catch (Exception ignored) {}
        return map;
    }

    public String enroll() {
        try {
            HttpResponse<String> resp = api.postEmpty(
                    COURSE_URL + "/api/v1/enrollments/courses/" + selectedCourseId,
                    session.getAccessToken());
            if (resp.statusCode() == 201) return "dashboard?faces-redirect=true";
            JsonNode err = api.readTree(resp.body());
            FacesMessageUtil.warn(err.path("message").asText("No se pudo inscribir."));
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al inscribirse.");
        }
        loadEnrolledIds();
        return null;
    }

    public String createCourse() {
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",       newTitle       != null ? newTitle       : "");
            bodyMap.put("description", newDescription != null ? newDescription : "");
            bodyMap.put("maxStudents", newMaxStudents);
            bodyMap.put("status",      newStatus      != null ? newStatus      : "DRAFT");
            HttpResponse<String> resp = api.post(
                    COURSE_URL + "/api/v1/courses", api.toJson(bodyMap), session.getAccessToken());
            if (resp.statusCode() == 201) {
                FacesMessageUtil.info("Curso creado exitosamente.");
                return "courses?faces-redirect=true";
            }
            JsonNode err = api.readTree(resp.body());
            FacesMessageUtil.warn(err.path("message").asText("No se pudo crear el curso."));
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al crear el curso.");
        }
        return null;
    }

    public boolean isEnrolled(String courseId) { return enrolledCourseIds.contains(courseId); }

    private void loadUnlockedSupplementaryLessons(String courseId) {
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/adaptive-rules/unlocked-supplementary?courseId=" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body()).forEach(id -> unlockedSupplementaryLessonIds.add(id.asText()));
            }
        } catch (Exception e) {
            LOG.warning("[CourseBean] loadUnlockedSupplementaryLessons error: " + e.getMessage());
        }
    }

    public boolean isSupplementaryUnlocked(String lessonId) {
        return unlockedSupplementaryLessonIds.contains(lessonId);
    }

    public double  getProgressPct()              { return Math.round(progressPct * 10.0) / 10.0; }
    public int     getCompletedLessons()         { return completedLessons; }
    public int     getTotalLessons()             { return totalLessons; }
    public String  getFirstUncompletedLessonId() { return firstUncompletedLessonId; }

    public List<CourseData>     getCourses()           { return courses; }
    public CourseData           getCourseDetail()      { return courseDetail; }
    public List<ModuleData>     getModules()           { return modules; }
    public List<AssessmentData> getCourseAssessments() { return courseAssessments; }
    public Set<String>          getEnrolledCourseIds() { return enrolledCourseIds; }
    public String               getSelectedCourseId()  { return selectedCourseId; }
    public void             setSelectedCourseId(String id) { this.selectedCourseId = id; }
    public String           getNewTitle()          { return newTitle; }
    public void             setNewTitle(String t)  { this.newTitle = t; }
    public String           getNewDescription()    { return newDescription; }
    public void             setNewDescription(String d) { this.newDescription = d; }
    public int              getNewMaxStudents()    { return newMaxStudents; }
    public void             setNewMaxStudents(int n) { this.newMaxStudents = n; }
    public String           getNewStatus()         { return newStatus; }
    public void             setNewStatus(String s) { this.newStatus = s; }
}
