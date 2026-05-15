package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "threads", schema = "collaboration")
public class Thread {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forum_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_thread_forum"))
    private Forum forum;

    @NotBlank
    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Post> posts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }

    public UUID       getId()       { return id; }
    public Forum      getForum()    { return forum; }
    public String     getTitle()    { return title; }
    public UUID       getAuthorId() { return authorId; }
    public boolean    isPinned()    { return pinned; }
    public boolean    isLocked()    { return locked; }
    public List<Post> getPosts()    { return posts; }
    public Instant    getCreatedAt(){ return createdAt; }
    public Instant    getDeletedAt(){ return deletedAt; }

    public void setForum(Forum forum)    { this.forum = forum; }
    public void setTitle(String title)   { this.title = title; }
    public void setAuthorId(UUID a)      { this.authorId = a; }
    public void setPinned(boolean p)     { this.pinned = p; }
    public void setLocked(boolean l)     { this.locked = l; }
}
