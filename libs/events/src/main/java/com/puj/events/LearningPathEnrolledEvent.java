package com.puj.events;

import java.util.List;

public class LearningPathEnrolledEvent extends BaseEvent {

    private String       userId;
    private String       learningPathId;
    private String       learningPathTitle;
    private List<String> courseIds;

    public LearningPathEnrolledEvent() { super(); }

    public LearningPathEnrolledEvent(String userId, String learningPathId,
                                     String learningPathTitle, List<String> courseIds) {
        super("LEARNING_PATH_ENROLLED", "course-service");
        this.userId            = userId;
        this.learningPathId    = learningPathId;
        this.learningPathTitle = learningPathTitle;
        this.courseIds         = courseIds;
    }

    public String       getUserId()            { return userId; }
    public String       getLearningPathId()    { return learningPathId; }
    public String       getLearningPathTitle() { return learningPathTitle; }
    public List<String> getCourseIds()         { return courseIds; }

    public void setUserId(String userId)                      { this.userId = userId; }
    public void setLearningPathId(String learningPathId)      { this.learningPathId = learningPathId; }
    public void setLearningPathTitle(String learningPathTitle){ this.learningPathTitle = learningPathTitle; }
    public void setCourseIds(List<String> courseIds)          { this.courseIds = courseIds; }
}
