package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_contents", schema = "courses")
public class LessonContent {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType = "TEXT";

    @Column(name = "content_url", columnDefinition = "TEXT")
    private String contentUrl;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID    getId()          { return id; }
    public Lesson  getLesson()      { return lesson; }
    public String  getTitle()       { return title; }
    public String  getDescription() { return description; }
    public String  getContentType() { return contentType; }
    public String  getContentUrl()  { return contentUrl; }
    public int     getOrderIndex()  { return orderIndex; }
    public Instant getCreatedAt()   { return createdAt; }

    public void setLesson(Lesson l)       { this.lesson = l; }
    public void setTitle(String t)        { this.title = t; }
    public void setDescription(String d)  { this.description = d; }
    public void setContentType(String t)  { this.contentType = t != null ? t : "TEXT"; }
    public void setContentUrl(String u)   { this.contentUrl = u; }
    public void setOrderIndex(int i)      { this.orderIndex = i; }
}
