package com.puj.events;

public class ForumPostCreatedEvent extends BaseEvent {

    private String  userId;
    private String  forumId;
    private String  courseId;
    private boolean isThread;

    public ForumPostCreatedEvent() { super(); }

    public ForumPostCreatedEvent(String userId, String forumId, String courseId, boolean isThread) {
        super("FORUM_POST_CREATED", "collaboration-service");
        this.userId   = userId;
        this.forumId  = forumId;
        this.courseId = courseId;
        this.isThread = isThread;
    }

    public String  getUserId()   { return userId; }
    public String  getForumId()  { return forumId; }
    public String  getCourseId() { return courseId; }
    public boolean isThread()    { return isThread; }

    public void setUserId(String userId)   { this.userId = userId; }
    public void setForumId(String forumId) { this.forumId = forumId; }
    public void setCourseId(String courseId){ this.courseId = courseId; }
    public void setThread(boolean isThread){ this.isThread = isThread; }
}
