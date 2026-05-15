package com.puj.events;

import java.util.Map;

public class EmailNotificationEvent extends BaseEvent {

    public enum EmailType {
        WELCOME, PASSWORD_RESET, ENROLLMENT_CONFIRMED,
        ASSESSMENT_GRADED, COURSE_COMPLETED
    }

    private String              recipientEmail;
    private String              recipientName;
    private EmailType           emailType;
    private Map<String, String> templateParams;

    public EmailNotificationEvent() { super(); }

    public EmailNotificationEvent(String recipientEmail, String recipientName,
                                   EmailType emailType, Map<String, String> templateParams) {
        super("EMAIL_NOTIFICATION", "platform");
        this.recipientEmail  = recipientEmail;
        this.recipientName   = recipientName;
        this.emailType       = emailType;
        this.templateParams  = templateParams;
    }

    public String              getRecipientEmail()  { return recipientEmail; }
    public String              getRecipientName()   { return recipientName; }
    public EmailType           getEmailType()       { return emailType; }
    public Map<String, String> getTemplateParams()  { return templateParams; }

    public void setRecipientEmail(String e)              { this.recipientEmail = e; }
    public void setRecipientName(String n)               { this.recipientName = n; }
    public void setEmailType(EmailType t)                { this.emailType = t; }
    public void setTemplateParams(Map<String, String> p) { this.templateParams = p; }
}
