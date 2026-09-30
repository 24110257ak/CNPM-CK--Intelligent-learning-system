package com.lms.model;

import java.time.LocalDateTime;

/**
 * POJO đại diện bảng [question_comments] - Thảo luận và bình luận phản biện từng câu hỏi.
 */
public class QuestionComment {

    private int commentId;
    private int questionId;
    private int userId;
    private String username;
    private String userFullName;
    private String userRole;
    private Integer parentCommentId;
    private String content;
    private LocalDateTime createdAt;

    public QuestionComment() {}

    public QuestionComment(int questionId, int userId, Integer parentCommentId, String content) {
        this.questionId = questionId;
        this.userId = userId;
        this.parentCommentId = parentCommentId;
        this.content = content;
    }

    public int getCommentId() { return commentId; }
    public void setCommentId(int commentId) { this.commentId = commentId; }

    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public Integer getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Integer parentCommentId) { this.parentCommentId = parentCommentId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
