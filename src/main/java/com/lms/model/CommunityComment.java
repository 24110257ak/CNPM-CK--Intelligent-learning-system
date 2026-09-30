package com.lms.model;

import java.sql.Timestamp;

/**
 * Model biểu diễn một bình luận bên dưới bài viết diễn đàn cộng đồng.
 */
public class CommunityComment {
    private int commentId;
    private int postId;
    private int userId;
    private String content;
    private Timestamp createdAt;

    // Thông tin người bình luận
    private String authorName;
    private String authorUsername;
    private String authorRole;

    public CommunityComment() {}

    public int getCommentId() { return commentId; }
    public void setCommentId(int commentId) { this.commentId = commentId; }

    public int getPostId() { return postId; }
    public void setPostId(int postId) { this.postId = postId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }

    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
}
