package com.lms.model;

import java.time.LocalDateTime;

/**
 * POJO đại diện bảng [question_ratings] - Đánh giá độ tin cậy, Upvote / Downvote và Báo lỗi câu hỏi.
 */
public class QuestionRating {

    private int ratingId;
    private int questionId;
    private int userId;
    private String ratingType;   // UPVOTE | DOWNVOTE | REPORT_ERROR
    private String reportReason; // Lý do báo lỗi / ảo giác AI
    private LocalDateTime createdAt;

    public QuestionRating() {}

    public QuestionRating(int questionId, int userId, String ratingType, String reportReason) {
        this.questionId = questionId;
        this.userId = userId;
        this.ratingType = ratingType;
        this.reportReason = reportReason;
    }

    public int getRatingId() { return ratingId; }
    public void setRatingId(int ratingId) { this.ratingId = ratingId; }

    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getRatingType() { return ratingType; }
    public void setRatingType(String ratingType) { this.ratingType = ratingType; }

    public String getReportReason() { return reportReason; }
    public void setReportReason(String reportReason) { this.reportReason = reportReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
