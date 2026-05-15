package com.puj.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserRegisteredEvent.class,     name = "USER_REGISTERED"),
        @JsonSubTypes.Type(value = CourseEnrolledEvent.class,     name = "COURSE_ENROLLED"),
        @JsonSubTypes.Type(value = AssessmentSubmittedEvent.class, name = "ASSESSMENT_SUBMITTED"),
        @JsonSubTypes.Type(value = EmailNotificationEvent.class,  name = "EMAIL_NOTIFICATION")
})
public abstract class BaseEvent {

    private String  eventId;
    private String  eventType;
    private Instant occurredAt;
    private String  sourceService;

    protected BaseEvent() {
        this.eventId    = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
    }

    protected BaseEvent(String eventType, String sourceService) {
        this();
        this.eventType     = eventType;
        this.sourceService = sourceService;
    }

    public String  getEventId()      { return eventId; }
    public String  getEventType()    { return eventType; }
    public Instant getOccurredAt()   { return occurredAt; }
    public String  getSourceService(){ return sourceService; }

    public void setEventId(String eventId)           { this.eventId = eventId; }
    public void setEventType(String eventType)       { this.eventType = eventType; }
    public void setOccurredAt(Instant occurredAt)    { this.occurredAt = occurredAt; }
    public void setSourceService(String s)           { this.sourceService = s; }
}
