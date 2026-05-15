package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", schema = "collaboration",
        indexes = @Index(name = "idx_chat_group_ts", columnList = "group_id, sent_at DESC"))
public class ChatMessage {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_email", nullable = false, length = 255)
    private String authorEmail;

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        sentAt = Instant.now();
    }

    public UUID    getId()          { return id; }
    public UUID    getGroupId()     { return groupId; }
    public UUID    getAuthorId()    { return authorId; }
    public String  getAuthorEmail() { return authorEmail; }
    public String  getContent()     { return content; }
    public Instant getSentAt()      { return sentAt; }

    public void setGroupId(UUID groupId)         { this.groupId = groupId; }
    public void setAuthorId(UUID authorId)        { this.authorId = authorId; }
    public void setAuthorEmail(String email)      { this.authorEmail = email; }
    public void setContent(String content)        { this.content = content; }
}
