package com.puj.events;

public class CourseEnrolledEvent extends BaseEvent {

    private String enrollmentId;
    private String userId;
    private String courseId;
    private String courseTitle;

    public CourseEnrolledEvent() { super(); }

    public CourseEnrolledEvent(String enrollmentId, String userId,
                               String courseId, String courseTitle) {
        super("COURSE_ENROLLED", "course-service");
        this.enrollmentId = enrollmentId;
        this.userId       = userId;
        this.courseId     = courseId;
        this.courseTitle  = courseTitle;
    }

    public String getEnrollmentId() { return enrollmentId; }
    public String getUserId()       { return userId; }
    public String getCourseId()     { return courseId; }
    public String getCourseTitle()  { return courseTitle; }

    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }
    public void setUserId(String userId)             { this.userId = userId; }
    public void setCourseId(String courseId)         { this.courseId = courseId; }
    public void setCourseTitle(String courseTitle)   { this.courseTitle = courseTitle; }
}
