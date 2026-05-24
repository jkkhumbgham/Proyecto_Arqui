package com.puj.events;

public class CertificateIssuedEvent extends BaseEvent {

    private String userId;
    private String courseId;
    private String courseTitle;
    private String studentName;
    private String certificateUrl;
    private String verificationCode;

    public CertificateIssuedEvent() { super(); }

    public CertificateIssuedEvent(String userId, String courseId, String courseTitle,
                                   String studentName, String certificateUrl, String verificationCode) {
        super("CERTIFICATE_ISSUED", "analytics-service");
        this.userId           = userId;
        this.courseId         = courseId;
        this.courseTitle      = courseTitle;
        this.studentName      = studentName;
        this.certificateUrl   = certificateUrl;
        this.verificationCode = verificationCode;
    }

    public String getUserId()           { return userId; }
    public String getCourseId()         { return courseId; }
    public String getCourseTitle()      { return courseTitle; }
    public String getStudentName()      { return studentName; }
    public String getCertificateUrl()   { return certificateUrl; }
    public String getVerificationCode() { return verificationCode; }

    public void setUserId(String userId)                     { this.userId = userId; }
    public void setCourseId(String courseId)                 { this.courseId = courseId; }
    public void setCourseTitle(String courseTitle)           { this.courseTitle = courseTitle; }
    public void setStudentName(String studentName)           { this.studentName = studentName; }
    public void setCertificateUrl(String certificateUrl)     { this.certificateUrl = certificateUrl; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }
}
