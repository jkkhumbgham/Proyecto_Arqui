package com.puj.events;

import java.math.BigDecimal;

public class AssessmentSubmittedEvent extends BaseEvent {

    private String     submissionId;
    private String     userId;
    private String     assessmentId;
    private String     courseId;
    private String     lessonId;
    private BigDecimal score;
    private BigDecimal maxScore;
    private boolean    passed;
    private long       durationSeconds;

    public AssessmentSubmittedEvent() { super(); }

    public AssessmentSubmittedEvent(String submissionId, String userId, String assessmentId,
                                    String courseId, String lessonId, BigDecimal score,
                                    BigDecimal maxScore, boolean passed, long durationSeconds) {
        super("ASSESSMENT_SUBMITTED", "assessment-service");
        this.submissionId    = submissionId;
        this.userId          = userId;
        this.assessmentId    = assessmentId;
        this.courseId        = courseId;
        this.lessonId        = lessonId;
        this.score           = score;
        this.maxScore        = maxScore;
        this.passed          = passed;
        this.durationSeconds = durationSeconds;
    }

    public String     getSubmissionId()    { return submissionId; }
    public String     getUserId()          { return userId; }
    public String     getAssessmentId()    { return assessmentId; }
    public String     getCourseId()        { return courseId; }
    public String     getLessonId()        { return lessonId; }
    public BigDecimal getScore()           { return score; }
    public BigDecimal getMaxScore()        { return maxScore; }
    public boolean    isPassed()           { return passed; }
    public long       getDurationSeconds() { return durationSeconds; }

    public void setSubmissionId(String s)        { this.submissionId = s; }
    public void setUserId(String u)              { this.userId = u; }
    public void setAssessmentId(String a)        { this.assessmentId = a; }
    public void setCourseId(String c)            { this.courseId = c; }
    public void setLessonId(String l)            { this.lessonId = l; }
    public void setScore(BigDecimal s)           { this.score = s; }
    public void setMaxScore(BigDecimal m)        { this.maxScore = m; }
    public void setPassed(boolean p)             { this.passed = p; }
    public void setDurationSeconds(long d)       { this.durationSeconds = d; }
}
