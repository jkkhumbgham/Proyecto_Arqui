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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Named
@RequestScoped
public class LessonViewerBean {

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    private String  lessonId;
    private String  courseId;
    private String  title;
    private String  content;
    private Integer durationMinutes;
    private String  moduleTitle;
    private String  contentType = "TEXT";
    private String  contentUrl;

    private CourseAssessment  moduleAssessment;
    private List<NavLesson>   navLessons      = new ArrayList<>();
    private List<LessonContent> lessonContents  = new ArrayList<>();
    private String              selectedContentId;
    private String prevLessonId;
    private String nextLessonId;

    public static class LessonContent {
        private final String id, title, description, contentType, contentUrl;
        public LessonContent(String id, String title, String description,
                             String contentType, String contentUrl) {
            this.id = id; this.title = title; this.description = description;
            this.contentType = contentType; this.contentUrl = contentUrl;
        }
        public String getId()          { return id; }
        public String getTitle()       { return title; }
        public String getDescription() { return description; }
        public String getContentType() { return contentType; }
        public String getContentUrl()  { return contentUrl; }
    }

    private Set<String> completedLessonIds = new HashSet<>();
    private int    completedCount;
    private int    totalLessons;
    private double progressPct;
    private boolean alreadyCompleted;

    public static class CourseAssessment {
        private final String  id, title, description, linkedLessonId;
        private final Integer timeLimitMinutes;
        private final double  passingScorePct;
        private final int     maxAttempts, questionCount;

        public CourseAssessment(String id, String title, String description,
                                Integer timeLimitMinutes, double passingScorePct,
                                int maxAttempts, int questionCount, String linkedLessonId) {
            this.id = id; this.title = title; this.description = description;
            this.timeLimitMinutes = timeLimitMinutes; this.passingScorePct = passingScorePct;
            this.maxAttempts = maxAttempts; this.questionCount = questionCount;
            this.linkedLessonId = linkedLessonId;
        }
        public String  getId()               { return id; }
        public String  getTitle()            { return title; }
        public String  getDescription()      { return description; }
        public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
        public double  getPassingScorePct()  { return passingScorePct; }
        public int     getMaxAttempts()      { return maxAttempts; }
        public int     getQuestionCount()    { return questionCount; }
        public String  getLinkedLessonId()   { return linkedLessonId; }
    }

    public static class NavLesson {
        private final String  id, title, moduleTitle;
        private final int     orderIndex;
        private final boolean supplementary;
        private boolean completed;
        private String  assessmentId;
        private String  assessmentTitle;

        public NavLesson(String id, String title, String moduleTitle, int orderIndex, boolean supplementary) {
            this.id = id; this.title = title; this.moduleTitle = moduleTitle;
            this.orderIndex = orderIndex; this.supplementary = supplementary;
        }
        public String  getId()             { return id; }
        public String  getTitle()          { return title; }
        public String  getModuleTitle()    { return moduleTitle; }
        public int     getOrderIndex()     { return orderIndex; }
        public boolean isSupplementary()   { return supplementary; }
        public boolean isCompleted()       { return completed; }
        public void    setCompleted(boolean c)      { this.completed = c; }
        public String  getAssessmentId()   { return assessmentId; }
        public String  getAssessmentTitle(){ return assessmentTitle; }
        public void    setAssessmentId(String id)   { this.assessmentId = id; }
        public void    setAssessmentTitle(String t) { this.assessmentTitle = t; }
    }

    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        lessonId          = params.get("lessonId");
        courseId          = params.get("courseId");
        selectedContentId = params.get("contentId");

        if (lessonId == null || lessonId.isBlank()) return;

        loadLesson();
        loadLessonContents();
        if (courseId != null && !courseId.isBlank()) {
            loadProgress();
            loadNavigation();
            loadAssessment();
        }

        if ("1".equals(params.get("complete")) && session.hasRole("STUDENT")) {
            try {
                api.postEmpty(COURSE_URL + "/api/v1/lessons/" + lessonId + "/complete",
                        session.getAccessToken());
            } catch (Exception ignored) {}

            try {
                FacesContext fc = FacesContext.getCurrentInstance();
                String redirect = nextLessonId != null
                        ? "/views/lesson-viewer.xhtml?lessonId=" + nextLessonId + "&courseId=" + courseId
                        : "/views/course-detail.xhtml?courseId=" + courseId;
                fc.getExternalContext().redirect(redirect);
                fc.responseComplete();
            } catch (Exception ignored) {}
        }
    }

    private void loadLesson() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/lessons/" + lessonId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                title           = n.path("title").asText();
                content         = n.path("content").asText("");
                durationMinutes = n.path("durationMinutes").isNull() ? null : n.path("durationMinutes").asInt();
                contentType     = n.path("contentType").asText("TEXT");
                String url      = n.path("contentUrl").asText(null);
                contentUrl      = (url != null && !url.isBlank()) ? url : null;
                if (courseId == null || courseId.isBlank()) {
                    courseId = n.path("courseId").asText(null);
                }
            }
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar la lección.");
        }
    }

    private void loadLessonContents() {
        try {
            HttpResponse<String> resp = api.get(
                    COURSE_URL + "/api/v1/lessons/" + lessonId + "/contents",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode arr = api.readTree(resp.body());
                if (arr.isArray()) {
                    arr.forEach(n -> lessonContents.add(new LessonContent(
                            n.path("id").asText(),
                            n.path("title").asText(),
                            n.path("description").asText(""),
                            n.path("contentType").asText("TEXT"),
                            n.path("contentUrl").isNull() ? null : n.path("contentUrl").asText(null)
                    )));
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadProgress() {
        if (!session.hasRole("STUDENT")) return;
        try {
            HttpResponse<String> resp = api.get(
                    COURSE_URL + "/api/v1/courses/" + courseId + "/progress",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                n.path("completedLessonIds").forEach(id -> completedLessonIds.add(id.asText()));
                completedCount   = (int) n.path("completedCount").asLong();
                totalLessons     = (int) n.path("totalLessons").asLong();
                progressPct      = n.path("progressPct").asDouble();
                alreadyCompleted = completedLessonIds.contains(lessonId);
            }
        } catch (Exception ignored) {}
    }

    private void loadNavigation() {
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/courses/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;

            JsonNode course   = api.readTree(resp.body());
            boolean isStudent = session.hasRole("STUDENT");
            course.path("modules").forEach(m -> {
                String modTitle = m.path("title").asText();
                m.path("lessons").forEach(l -> {
                    boolean supp = l.path("supplementary").asBoolean(false);
                    String  lid  = l.path("id").asText();
                    if (isStudent && supp && !lid.equals(lessonId)) return;
                    NavLesson nav = new NavLesson(lid, l.path("title").asText(), modTitle,
                            l.path("orderIndex").asInt(), supp);
                    nav.setCompleted(completedLessonIds.contains(nav.getId()));
                    navLessons.add(nav);
                });
            });

            for (int i = 0; i < navLessons.size(); i++) {
                if (navLessons.get(i).getId().equals(lessonId)) {
                    prevLessonId = i > 0 ? navLessons.get(i - 1).getId() : null;
                    nextLessonId = i < navLessons.size() - 1 ? navLessons.get(i + 1).getId() : null;
                    moduleTitle  = navLessons.get(i).getModuleTitle();
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadAssessment() {
        Set<String> moduleLessonIds = new HashSet<>();
        for (NavLesson nl : currentModuleLessons()) moduleLessonIds.add(nl.getId());
        if (moduleLessonIds.isEmpty()) return;
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/assessments/course/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;
            JsonNode arr = api.readTree(resp.body());
            if (!arr.isArray()) return;

            Map<String, JsonNode> lessonAssessmentNode = new HashMap<>();
            for (JsonNode a : arr) {
                if (a.path("lessonId").isNull() || a.path("lessonId").isMissingNode()) continue;
                String aLessonId = a.path("lessonId").asText(null);
                if (aLessonId != null && moduleLessonIds.contains(aLessonId)) {
                    lessonAssessmentNode.put(aLessonId, a);
                }
            }

            for (NavLesson nl : navLessons) {
                JsonNode a = lessonAssessmentNode.get(nl.getId());
                if (a != null) {
                    nl.setAssessmentId(a.path("id").asText());
                    nl.setAssessmentTitle(a.path("title").asText(""));
                }
            }

            JsonNode currentA = lessonAssessmentNode.get(lessonId);
            if (currentA != null) {
                loadAssessmentDetail(currentA.path("id").asText(), lessonId);
            }
        } catch (Exception ignored) {}
    }

    private void loadAssessmentDetail(String assessmentId, String linkedLessonId) {
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/assessments/" + assessmentId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;
            JsonNode n     = api.readTree(resp.body());
            int      qCount = n.path("questions").isArray() ? n.path("questions").size() : 0;
            moduleAssessment = new CourseAssessment(
                    assessmentId,
                    n.path("title").asText(),
                    n.path("description").asText(""),
                    n.path("timeLimitMinutes").isNull() ? null : n.path("timeLimitMinutes").asInt(),
                    n.path("passingScorePct").asDouble(60.0),
                    n.path("maxAttempts").asInt(3),
                    qCount,
                    linkedLessonId
            );
        } catch (Exception ignored) {}
    }

    private List<NavLesson> currentModuleLessons() {
        if (moduleTitle == null) return navLessons;
        return navLessons.stream().filter(n -> moduleTitle.equals(n.getModuleTitle())).toList();
    }

    public String toggleComplete() {
        if (lessonId == null || !session.hasRole("STUDENT")) return null;
        try {
            api.postEmpty(COURSE_URL + "/api/v1/lessons/" + lessonId + "/complete",
                    session.getAccessToken());
        } catch (Exception ignored) {}
        return "/views/lesson-viewer?faces-redirect=true&lessonId=" + lessonId + "&courseId=" + courseId;
    }

    public String markComplete() {
        if (lessonId == null || !session.hasRole("STUDENT")) return null;
        try {
            api.postEmpty(COURSE_URL + "/api/v1/lessons/" + lessonId + "/complete",
                    session.getAccessToken());
        } catch (Exception ignored) {}

        // @PostConstruct ran before Apply Request Values, so navLessons was never built.
        // Load navigation now that lessonId and courseId are bound via h:inputHidden.
        if (courseId != null) loadNavigation();

        if (nextLessonId != null) {
            return "/views/lesson-viewer?faces-redirect=true&lessonId=" + nextLessonId + "&courseId=" + courseId;
        }
        return "/views/course-detail?faces-redirect=true&courseId=" + courseId;
    }

    public boolean isCompleted(String id) { return completedLessonIds.contains(id); }

    public String  getLessonId()        { return lessonId; }
    public void    setLessonId(String v){ this.lessonId = v; }
    public String  getCourseId()        { return courseId; }
    public void    setCourseId(String v){ this.courseId = v; }
    public String  getTitle()           { return title; }
    public String  getContent()         { return content; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String  getModuleTitle()     { return moduleTitle; }
    public List<NavLesson> getNavLessons() { return navLessons; }
    public String  getPrevLessonId()    { return prevLessonId; }
    public String  getNextLessonId()    { return nextLessonId; }
    public boolean isLoaded()           { return title != null; }
    public int     getCompletedCount()  { return completedCount; }
    public int     getTotalLessons()    { return totalLessons; }
    public double  getProgressPct()     { return progressPct; }
    public boolean isAlreadyCompleted() { return alreadyCompleted; }
    public boolean isStudent()          { return session.hasRole("STUDENT"); }
    public String  getContentType()     { return contentType; }
    public String  getContentUrl()      { return contentUrl; }

    public CourseAssessment    getModuleAssessment()  { return moduleAssessment; }
    public List<LessonContent> getLessonContents()    { return lessonContents; }
    public String              getSelectedContentId() { return selectedContentId; }

    public List<NavLesson> getModuleNavLessons() {
        boolean isStudent = session.hasRole("STUDENT");
        return navLessons.stream()
                .filter(n -> moduleTitle == null || moduleTitle.equals(n.getModuleTitle()))
                .filter(n -> !isStudent || !n.isSupplementary() || n.getId().equals(lessonId))
                .toList();
    }

    public LessonContent getSelectedContent() {
        if (lessonContents.isEmpty()) return null;
        if (selectedContentId != null) {
            for (LessonContent c : lessonContents) {
                if (c.getId().equals(selectedContentId)) return c;
            }
        }
        return lessonContents.get(0);
    }

    public String getSelectedEmbedUrl() {
        LessonContent c = getSelectedContent();
        if (c == null || c.getContentUrl() == null) return null;
        return toEmbedUrl(c.getContentUrl());
    }

    public String getEmbedUrl() {
        if (contentUrl == null) return null;
        return toEmbedUrl(contentUrl);
    }

    private static String toEmbedUrl(String url) {
        if (url == null) return null;
        if (url.contains("youtube.com/watch?v=")) {
            return "https://www.youtube.com/embed/" + url.replaceAll(".*[?&]v=([^&]+).*", "$1");
        }
        if (url.contains("youtu.be/")) {
            String vid = url.substring(url.lastIndexOf('/') + 1).replaceAll("\\?.*", "");
            return "https://www.youtube.com/embed/" + vid;
        }
        return url;
    }
}
