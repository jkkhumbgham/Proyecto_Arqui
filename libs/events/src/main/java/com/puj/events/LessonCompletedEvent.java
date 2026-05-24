package com.puj.events;

public class LessonCompletedEvent extends BaseEvent {

    private String userId;
    private String lessonId;
    private String courseId;

    public LessonCompletedEvent() { super(); }

    public LessonCompletedEvent(String userId, String lessonId, String courseId) {
        super("LESSON_COMPLETED", "course-service");
        this.userId   = userId;
        this.lessonId = lessonId;
        this.courseId = courseId;
    }

    public String getUserId()   { return userId; }
    public String getLessonId() { return lessonId; }
    public String getCourseId() { return courseId; }

    public void setUserId(String userId)   { this.userId = userId; }
    public void setLessonId(String lessonId){ this.lessonId = lessonId; }
    public void setCourseId(String courseId){ this.courseId = courseId; }
}
