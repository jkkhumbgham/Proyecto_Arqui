package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ComponentSystemEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Named
@SessionScoped
public class AssessmentBuilderBean implements Serializable {

    private static final long   serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AssessmentBuilderBean.class.getName());

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    @PostConstruct
    public void postConstruct() {
        LOG.fine("[AssessmentBuilderBean] Bean created (new instance)");
    }

    public static class LessonOption implements Serializable {
        private final String id, label;
        public LessonOption(String id, String label) { this.id = id; this.label = label; }
        public String getId()    { return id; }
        public String getLabel() { return label; }
    }

    private String  title;
    private String  description;
    private String  courseId         = "";
    private String  moduleId         = "";
    private String  lessonId         = "";
    private String  editAssessmentId = "";
    private double  passingScorePct  = 60.0;
    private int     maxAttempts      = 3;

    private List<LessonOption> lessonOptions              = new ArrayList<>();
    private List<LessonOption> supplementaryLessonOptions = new ArrayList<>();
    private List<QuestionForm> questions                  = new ArrayList<>();
    private String             lastCreatedId;

    private double  adaptiveThreshold             = 60.0;
    private String  adaptiveMessage               = "";
    private String  adaptiveSupplementaryLessonId = "";
    private String  adaptiveRuleId                = "";

    public static class QuestionForm implements Serializable {
        private String text        = "";
        private String type        = "SINGLE_CHOICE";
        private double points      = 1.0;
        private String shortAnswer = "";
        private List<OptionForm> options = new ArrayList<>();

        public QuestionForm() {
            options.add(new OptionForm("", false));
            options.add(new OptionForm("", false));
            options.add(new OptionForm("", false));
            options.add(new OptionForm("", false));
        }

        public String getText()              { return text; }
        public void   setText(String t)      { this.text = t; }
        public String getType()              { return type; }
        public void   setType(String t)      { this.type = t; }
        public double getPoints()            { return points; }
        public void   setPoints(double p)    { this.points = p; }
        public String getShortAnswer()       { return shortAnswer; }
        public void   setShortAnswer(String s) { this.shortAnswer = s; }
        public List<OptionForm> getOptions() { return options; }
    }

    public static class OptionForm implements Serializable {
        private String  text    = "";
        private boolean correct = false;

        public OptionForm() {}
        public OptionForm(String text, boolean correct) { this.text = text; this.correct = correct; }

        public String  getText()             { return text; }
        public void    setText(String t)     { this.text = t; }
        public boolean isCorrect()           { return correct; }
        public void    setCorrect(boolean c) { this.correct = c; }
    }

    public void reset() {
        title = null; description = null; courseId = ""; moduleId = ""; lessonId = "";
        editAssessmentId = "";
        passingScorePct = 60.0; maxAttempts = 3;
        questions.clear();
        lessonOptions.clear();
        lastCreatedId = null;
        adaptiveThreshold = 60.0;
        adaptiveMessage = ""; adaptiveSupplementaryLessonId = ""; adaptiveRuleId = "";
        supplementaryLessonOptions.clear();
    }

    public void onPreRenderView(ComponentSystemEvent event) {
        initFromRequest();
    }

    public void onPreRenderViewEdit(ComponentSystemEvent event) {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx.isPostback()) {
            if (lessonOptions.isEmpty() && !courseId.isBlank() && !moduleId.isBlank())
                loadModuleLessons(courseId, moduleId);
            else if (supplementaryLessonOptions.isEmpty() && !courseId.isBlank())
                loadSupplementaryLessons(courseId);
            sanitizeSelections();
            return;
        }
        Map<String, String> params = ctx.getExternalContext().getRequestParameterMap();
        String aid = params.get("assessmentId");
        String cid = params.get("courseId");
        if (aid == null || aid.isBlank() || cid == null || cid.isBlank()) return;
        reset();
        this.courseId = cid;
        this.editAssessmentId = aid;
        loadAssessmentForEdit(aid);
    }

    private void loadAssessmentForEdit(String assessmentId) {
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/assessments/" + assessmentId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;
            JsonNode n = api.readTree(resp.body());
            this.title          = n.path("title").asText("");
            this.description    = n.path("description").asText("");
            this.passingScorePct = n.path("passingScorePct").asDouble(60.0);
            this.maxAttempts    = n.path("maxAttempts").asInt(3);
            String lid = n.path("lessonId").isNull() || n.path("lessonId").isMissingNode()
                    ? "" : n.path("lessonId").asText("");
            this.lessonId = lid;

            questions.clear();
            n.path("questions").forEach(q -> {
                QuestionForm qf = new QuestionForm();
                qf.setText(q.path("questionText").asText(""));
                qf.setType(q.path("questionType").asText("SINGLE_CHOICE"));
                qf.setPoints(q.path("points").asDouble(1.0));
                qf.getOptions().clear();
                if ("SHORT_ANSWER".equals(qf.getType())) {
                    StringBuilder sb = new StringBuilder();
                    q.path("options").forEach(o -> {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(o.path("text").asText(""));
                    });
                    qf.setShortAnswer(sb.toString());
                    while (qf.getOptions().size() < 4) qf.getOptions().add(new OptionForm());
                } else {
                    q.path("options").forEach(o -> {
                        OptionForm of = new OptionForm();
                        of.setText(o.path("text").asText(""));
                        of.setCorrect(o.path("correct").asBoolean(false));
                        qf.getOptions().add(of);
                    });
                    while (qf.getOptions().size() < 4) qf.getOptions().add(new OptionForm());
                }
                questions.add(qf);
            });

            if (!lid.isBlank()) {
                try {
                    HttpResponse<String> lr = api.get(COURSE_URL + "/api/v1/lessons/" + lid,
                            session.getAccessToken());
                    if (lr.statusCode() == 200) {
                        JsonNode ln  = api.readTree(lr.body());
                        String   mid = ln.path("moduleId").asText("");
                        if (!mid.isBlank()) { this.moduleId = mid; loadModuleLessons(courseId, mid); }
                    }
                } catch (Exception ignored) {}
            }
            if (supplementaryLessonOptions.isEmpty()) loadSupplementaryLessons(courseId);
            loadAdaptiveRule(assessmentId);
            sanitizeSelections();
        } catch (Exception e) {
            LOG.warning("[AssessmentBuilderBean] loadAssessmentForEdit error: " + e.getMessage());
        }
    }

    private void sanitizeSelections() {
        if (lessonId == null) lessonId = "";
        if (adaptiveSupplementaryLessonId == null) adaptiveSupplementaryLessonId = "";
        if (!lessonId.isBlank()) {
            boolean found = lessonOptions.stream().anyMatch(lo -> lessonId.equals(lo.getId()));
            if (!found) {
                LOG.fine("[AssessmentBuilderBean] lessonId " + lessonId
                        + " not in lessonOptions — clearing");
                lessonId = "";
            }
        }
        if (!adaptiveSupplementaryLessonId.isBlank()) {
            boolean found = supplementaryLessonOptions.stream()
                    .anyMatch(lo -> adaptiveSupplementaryLessonId.equals(lo.getId()));
            if (!found) {
                LOG.fine("[AssessmentBuilderBean] adaptiveSupplementaryLessonId "
                        + adaptiveSupplementaryLessonId + " not in supplementaryLessonOptions — clearing");
                adaptiveSupplementaryLessonId = "";
            }
        }
    }

    private void loadAdaptiveRule(String assessmentId) {
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/adaptive-rules/assessments/" + assessmentId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                this.adaptiveRuleId   = n.path("id").asText("");
                this.adaptiveThreshold = n.path("scoreThresholdPct").asDouble(60.0);
                this.adaptiveMessage  = n.path("message").isNull() ? "" : n.path("message").asText("");
                this.adaptiveSupplementaryLessonId = n.path("supplementaryLessonId").isNull()
                        ? "" : n.path("supplementaryLessonId").asText("");
                LOG.fine("[AssessmentBuilderBean] loadAdaptiveRule found rule " + adaptiveRuleId);
            }
        } catch (Exception e) {
            LOG.warning("[AssessmentBuilderBean] loadAdaptiveRule error: " + e.getMessage());
        }
    }

    private void saveAdaptiveRule(String assessmentId, String cid,
                                   String supLessonId, double threshold,
                                   String message, String existingRuleId) {
        try {
            ObjectNode body = api.createObjectNode();
            if (existingRuleId != null && !existingRuleId.isBlank()) body.put("id", existingRuleId);
            body.put("assessmentId",          assessmentId);
            body.put("courseId",              cid);
            body.put("scoreThresholdPct",     threshold);
            body.put("supplementaryLessonId", supLessonId);
            if (message != null && !message.isBlank()) body.put("message", message);
            body.put("active", true);

            HttpResponse<String> resp = api.post(
                    ASSESSMENT_URL + "/api/v1/adaptive-rules",
                    body.toString(), session.getAccessToken());
            LOG.fine("[AssessmentBuilderBean] saveAdaptiveRule HTTP " + resp.statusCode());
        } catch (Exception e) {
            LOG.warning("[AssessmentBuilderBean] saveAdaptiveRule error: " + e.getMessage());
        }
    }

    public boolean isEditMode() { return editAssessmentId != null && !editAssessmentId.isBlank(); }

    public void initFromRequest() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx.isPostback()) {
            LOG.fine("[AssessmentBuilderBean] initFromRequest POSTBACK — courseId='" + courseId
                    + "' title='" + title + "' q=" + questions.size() + " lo=" + lessonOptions.size());
            if (lessonOptions.isEmpty() && !courseId.isBlank() && !moduleId.isBlank())
                loadModuleLessons(courseId, moduleId);
            else if (supplementaryLessonOptions.isEmpty() && !courseId.isBlank())
                loadSupplementaryLessons(courseId);
            return;
        }
        LOG.fine("[AssessmentBuilderBean] initFromRequest GET");
        reset();
        Map<String, String> params = ctx.getExternalContext().getRequestParameterMap();
        String cid = params.get("courseId");
        String mid = params.get("moduleId");
        if (cid != null && !cid.isBlank()) {
            this.courseId = cid;
            if (mid != null && !mid.isBlank()) {
                this.moduleId = mid;
                loadModuleLessons(cid, mid);
                if (!lessonOptions.isEmpty()) this.lessonId = lessonOptions.get(0).getId();
            } else {
                loadSupplementaryLessons(cid);
            }
        }
    }

    public void initForCourse(String courseId) {
        reset();
        this.courseId = courseId;
    }

    public String initAndNavigate(String courseId) {
        reset();
        this.courseId = courseId;
        return "/views/assessment-builder?faces-redirect=true";
    }

    public String initAndNavigateWithModule(String courseId, String moduleId) {
        reset();
        this.courseId = courseId;
        this.moduleId = moduleId;
        loadModuleLessons(courseId, moduleId);
        if (!lessonOptions.isEmpty()) {
            this.lessonId = lessonOptions.get(0).getId();
        }
        return "/views/assessment-builder?faces-redirect=true";
    }

    private void loadModuleLessons(String courseId, String moduleId) {
        lessonOptions.clear();
        if (courseId == null || courseId.isBlank() || moduleId == null || moduleId.isBlank()) return;
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/courses/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;
            JsonNode course = api.readTree(resp.body());
            for (JsonNode m : course.path("modules")) {
                if (moduleId.equals(m.path("id").asText())) {
                    int idx = 0;
                    for (JsonNode l : m.path("lessons")) {
                        idx++;
                        lessonOptions.add(new LessonOption(
                                l.path("id").asText(),
                                idx + ". " + l.path("title").asText()
                        ));
                    }
                    break;
                }
            }
            loadSupplementaryLessons(courseId, api.readTree(resp.body()));
            LOG.fine("[AssessmentBuilderBean] loadModuleLessons found " + lessonOptions.size() + " lessons");
        } catch (Exception e) {
            LOG.warning("[AssessmentBuilderBean] loadModuleLessons error: " + e.getMessage());
        }
    }

    private void loadSupplementaryLessons(String courseId) {
        if (courseId == null || courseId.isBlank()) return;
        try {
            HttpResponse<String> resp = api.get(COURSE_URL + "/api/v1/courses/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;
            loadSupplementaryLessons(courseId, api.readTree(resp.body()));
        } catch (Exception e) {
            LOG.warning("[AssessmentBuilderBean] loadSupplementaryLessons error: " + e.getMessage());
        }
    }

    private void loadSupplementaryLessons(String courseId, JsonNode course) {
        supplementaryLessonOptions.clear();
        int idx = 0;
        for (JsonNode m : course.path("modules")) {
            for (JsonNode l : m.path("lessons")) {
                if (l.path("supplementary").asBoolean(false)) {
                    idx++;
                    supplementaryLessonOptions.add(new LessonOption(
                            l.path("id").asText(),
                            idx + ". " + l.path("title").asText()
                    ));
                }
            }
        }
        LOG.fine("[AssessmentBuilderBean] supplementaryLessons found " + supplementaryLessonOptions.size());
    }

    public void addQuestion() {
        LOG.fine("[AssessmentBuilderBean] addQuestion — title='" + title + "' q=" + questions.size());
        questions.add(new QuestionForm());
    }

    public void removeQuestion(int index) {
        if (index >= 0 && index < questions.size()) {
            questions.remove(index);
        }
    }

    public String save() {
        if (title == null || title.isBlank()) {
            FacesMessageUtil.warn("El título es obligatorio.");
            return null;
        }
        if (courseId == null || courseId.isBlank()) {
            FacesMessageUtil.warn("Debes asociar la evaluación a un curso.");
            return null;
        }
        try {
            boolean    editMode = isEditMode();
            ObjectNode body     = api.createObjectNode();
            body.put("title", title);
            if (!editMode) body.put("courseId", courseId);
            if (lessonId != null && !lessonId.isBlank()) body.put("lessonId", lessonId);
            if (description != null) body.put("description", description);
            body.put("passingScorePct", passingScorePct);
            body.put("maxAttempts",     maxAttempts);

            ArrayNode qArr = body.putArray("questions");
            for (QuestionForm qf : questions) {
                if (qf.getText() == null || qf.getText().isBlank()) continue;
                ObjectNode qn = qArr.addObject();
                qn.put("text",   qf.getText());
                qn.put("type",   qf.getType());
                qn.put("points", qf.getPoints());
                ArrayNode oArr = qn.putArray("options");
                if ("SHORT_ANSWER".equals(qf.getType())) {
                    String sa = qf.getShortAnswer();
                    if (sa != null && !sa.isBlank()) {
                        for (String ans : sa.split(",")) {
                            String a = ans.trim();
                            if (!a.isEmpty()) {
                                ObjectNode on = oArr.addObject();
                                on.put("text", a);
                                on.put("correct", true);
                            }
                        }
                    }
                } else {
                    for (OptionForm of : qf.getOptions()) {
                        if (of.getText() == null || of.getText().isBlank()) continue;
                        ObjectNode on = oArr.addObject();
                        on.put("text",    of.getText());
                        on.put("correct", of.isCorrect());
                    }
                }
            }

            LOG.fine("[AssessmentBuilderBean] save body length=" + body.toString().length());

            HttpResponse<String> resp;
            if (editMode) {
                resp = api.put(ASSESSMENT_URL + "/api/v1/assessments/" + editAssessmentId,
                        body.toString(), session.getAccessToken(), Duration.ofSeconds(10));
            } else {
                resp = api.post(ASSESSMENT_URL + "/api/v1/assessments",
                        body.toString(), session.getAccessToken(), Duration.ofSeconds(10));
            }

            int expected = editMode ? 200 : 201;
            LOG.fine("[AssessmentBuilderBean] save response HTTP " + resp.statusCode());

            if (resp.statusCode() == expected) {
                try {
                    JsonNode result = api.readTree(resp.body());
                    lastCreatedId = result.path("id").asText();
                } catch (Exception ignored) {}
                FacesMessageUtil.info(editMode ? "Evaluación actualizada." : "Evaluación creada exitosamente.");
                String  cid     = courseId;
                String  aidRule = editMode ? editAssessmentId : lastCreatedId;
                boolean doRule  = !adaptiveSupplementaryLessonId.isBlank()
                        && aidRule != null && !aidRule.isBlank();
                String  supLid  = adaptiveSupplementaryLessonId;
                double  thresh  = adaptiveThreshold;
                String  ruleMsg = adaptiveMessage;
                String  ruleId  = adaptiveRuleId;
                reset();
                if (doRule) saveAdaptiveRule(aidRule, cid, supLid, thresh, ruleMsg, ruleId);
                return "/views/course-detail?faces-redirect=true&courseId=" + cid;
            }
            String errMsg = "No se pudo guardar la evaluación. Verifica los datos e intenta de nuevo.";
            try {
                String apiMsg = api.readTree(resp.body()).path("message").asText("");
                if (!apiMsg.isBlank()) errMsg = apiMsg;
            } catch (Exception ignored) {}
            LOG.warning("[AssessmentBuilderBean] save HTTP error " + resp.statusCode());
            FacesMessageUtil.warn(errMsg);
        } catch (Exception e) {
            LOG.warning("[AssessmentBuilderBean] save exception: " + e.getMessage());
            FacesMessageUtil.warn("No se pudo guardar la evaluación. Intenta de nuevo.");
        }
        return null;
    }

    public String  getTitle()               { return title; }
    public void    setTitle(String t)        { this.title = t; }
    public String  getDescription()          { return description; }
    public void    setDescription(String d)  { this.description = d; }
    public String  getCourseId()             { return courseId; }
    public void    setCourseId(String id)    { this.courseId = id; }
    public String  getModuleId()             { return moduleId; }
    public void    setModuleId(String id)    { this.moduleId = id; }
    public String  getLessonId()             { return lessonId; }
    public void    setLessonId(String id)    { this.lessonId = id == null ? "" : id; }
    public List<LessonOption> getLessonOptions()              { return lessonOptions; }
    public List<LessonOption> getSupplementaryLessonOptions() { return supplementaryLessonOptions; }
    public double  getPassingScorePct()      { return passingScorePct; }
    public void    setPassingScorePct(double p) { this.passingScorePct = p; }
    public int     getMaxAttempts()          { return maxAttempts; }
    public void    setMaxAttempts(int m)     { this.maxAttempts = m; }
    public List<QuestionForm> getQuestions() { return questions; }
    public String  getLastCreatedId()         { return lastCreatedId; }
    public String  getEditAssessmentId()      { return editAssessmentId; }
    public void    setEditAssessmentId(String id) { this.editAssessmentId = id; }

    public double  getAdaptiveThreshold()                    { return adaptiveThreshold; }
    public void    setAdaptiveThreshold(double t)            { this.adaptiveThreshold = t; }
    public String  getAdaptiveMessage()                      { return adaptiveMessage; }
    public void    setAdaptiveMessage(String m)              { this.adaptiveMessage = m; }
    public String  getAdaptiveSupplementaryLessonId()        { return adaptiveSupplementaryLessonId; }
    public void    setAdaptiveSupplementaryLessonId(String l){ this.adaptiveSupplementaryLessonId = l == null ? "" : l; }
    public String  getAdaptiveRuleId()                       { return adaptiveRuleId; }
}
