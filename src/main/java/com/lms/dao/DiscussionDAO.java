package com.lms.dao;

import com.lms.model.QuestionComment;
import com.lms.model.QuestionRating;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object quản lý Diễn đàn thảo luận và Hệ thống tín nhiệm / Báo lỗi câu hỏi.
 */
public class DiscussionDAO {

    /**
     * Thêm bình luận mới vào câu hỏi.
     */
    public int addComment(QuestionComment comment) {
        String sql = "INSERT INTO question_comments (question_id, user_id, parent_comment_id, content) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, comment.getQuestionId());
            ps.setInt(2, comment.getUserId());
            if (comment.getParentCommentId() != null && comment.getParentCommentId() > 0) {
                ps.setInt(3, comment.getParentCommentId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, comment.getContent());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        comment.setCommentId(id);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Lấy toàn bộ bình luận của 1 câu hỏi, kèm thông tin người gửi (họ tên, username, role).
     */
    public List<QuestionComment> getCommentsByQuestion(int questionId) {
        List<QuestionComment> list = new ArrayList<>();
        String sql = "SELECT c.comment_id, c.question_id, c.user_id, c.parent_comment_id, c.content, c.created_at, "
                + "       u.username, u.full_name, u.role "
                + "FROM question_comments c "
                + "JOIN users u ON c.user_id = u.user_id "
                + "WHERE c.question_id = ? "
                + "ORDER BY c.created_at ASC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QuestionComment qc = new QuestionComment();
                    qc.setCommentId(rs.getInt("comment_id"));
                    qc.setQuestionId(rs.getInt("question_id"));
                    qc.setUserId(rs.getInt("user_id"));

                    int parentId = rs.getInt("parent_comment_id");
                    qc.setParentCommentId(rs.wasNull() ? null : parentId);

                    qc.setContent(rs.getString("content"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        qc.setCreatedAt(ts.toLocalDateTime());
                    }
                    qc.setUsername(rs.getString("username"));
                    qc.setUserFullName(rs.getString("full_name"));
                    qc.setUserRole(rs.getString("role"));

                    list.add(qc);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Xóa bình luận nếu là tác giả hoặc admin/teacher.
     */
    public boolean deleteComment(int commentId, int userId, boolean isPrivileged) {
        String sql = isPrivileged
                ? "DELETE FROM question_comments WHERE comment_id = ?"
                : "DELETE FROM question_comments WHERE comment_id = ? AND user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, commentId);
            if (!isPrivileged) {
                ps.setInt(2, userId);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đánh giá độ tin cậy (UPVOTE, DOWNVOTE, REPORT_ERROR).
     * Tương thích cả PostgreSQL lẫn SQL Server bằng cách kiểm tra trước.
     */
    public boolean rateQuestion(int questionId, int userId, String ratingType, String reportReason) {
        String checkSql = "SELECT rating_id, rating_type FROM question_ratings WHERE question_id = ? AND user_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {

            checkPs.setInt(1, questionId);
            checkPs.setInt(2, userId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    int existingId = rs.getInt("rating_id");
                    String oldType = rs.getString("rating_type");

                    // Nếu click lại cùng loại vote (UPVOTE / DOWNVOTE) thì hủy vote (toggle)
                    if (oldType.equalsIgnoreCase(ratingType) && !"REPORT_ERROR".equalsIgnoreCase(ratingType)) {
                        String delSql = "DELETE FROM question_ratings WHERE rating_id = ?";
                        try (PreparedStatement delPs = conn.prepareStatement(delSql)) {
                            delPs.setInt(1, existingId);
                            return delPs.executeUpdate() > 0;
                        }
                    } else {
                        // Cập nhật rating mới
                        String updateSql = "UPDATE question_ratings SET rating_type = ?, report_reason = ?, created_at = CURRENT_TIMESTAMP WHERE rating_id = ?";
                        try (PreparedStatement updPs = conn.prepareStatement(updateSql)) {
                            updPs.setString(1, ratingType);
                            updPs.setString(2, reportReason);
                            updPs.setInt(3, existingId);
                            return updPs.executeUpdate() > 0;
                        }
                    }
                } else {
                    // Thêm mới
                    String insertSql = "INSERT INTO question_ratings (question_id, user_id, rating_type, report_reason) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement insPs = conn.prepareStatement(insertSql)) {
                        insPs.setInt(1, questionId);
                        insPs.setInt(2, userId);
                        insPs.setString(3, ratingType);
                        insPs.setString(4, reportReason);
                        return insPs.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy trạng thái vote của người dùng hiện tại đối với câu hỏi.
     */
    public Optional<QuestionRating> getUserRating(int questionId, int userId) {
        String sql = "SELECT rating_id, question_id, user_id, rating_type, report_reason, created_at "
                + "FROM question_ratings WHERE question_id = ? AND user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, questionId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    QuestionRating r = new QuestionRating();
                    r.setRatingId(rs.getInt("rating_id"));
                    r.setQuestionId(rs.getInt("question_id"));
                    r.setUserId(rs.getInt("user_id"));
                    r.setRatingType(rs.getString("rating_type"));
                    r.setReportReason(rs.getString("report_reason"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
                    return Optional.of(r);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Thống kê độ tín nhiệm của câu hỏi (Upvotes, Downvotes, Báo lỗi, % Điểm tín nhiệm).
     */
    public Map<String, Object> getQuestionCredibility(int questionId) {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT rating_type, COUNT(*) as cnt FROM question_ratings WHERE question_id = ? GROUP BY rating_type";

        int upvotes = 0;
        int downvotes = 0;
        int reports = 0;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("rating_type");
                    int count = rs.getInt("cnt");
                    if ("UPVOTE".equalsIgnoreCase(type)) upvotes = count;
                    else if ("DOWNVOTE".equalsIgnoreCase(type)) downvotes = count;
                    else if ("REPORT_ERROR".equalsIgnoreCase(type)) reports = count;
                }
            }

            stats.put("upvotes", upvotes);
            stats.put("downvotes", downvotes);
            stats.put("reports", reports);

            int totalVotes = upvotes + downvotes;
            double scorePercent = 100.0;
            if (totalVotes > 0) {
                scorePercent = ((double) upvotes / totalVotes) * 100.0;
            }
            stats.put("scorePercent", Math.round(scorePercent * 10.0) / 10.0);

            // Lấy danh sách báo lỗi gần nhất
            if (reports > 0) {
                String repSql = "SELECT r.report_reason, r.created_at, u.username, u.full_name "
                        + "FROM question_ratings r JOIN users u ON r.user_id = u.user_id "
                        + "WHERE r.question_id = ? AND r.rating_type = 'REPORT_ERROR' AND r.report_reason IS NOT NULL "
                        + "ORDER BY r.created_at DESC LIMIT 5";
                List<Map<String, String>> reportList = new ArrayList<>();
                try (PreparedStatement repPs = conn.prepareStatement(repSql)) {
                    repPs.setInt(1, questionId);
                    try (ResultSet repRs = repPs.executeQuery()) {
                        while (repRs.next()) {
                            Map<String, String> rep = new HashMap<>();
                            rep.put("reporter", repRs.getString("full_name") + " (" + repRs.getString("username") + ")");
                            rep.put("reason", repRs.getString("report_reason"));
                            rep.put("time", String.valueOf(repRs.getTimestamp("created_at")));
                            reportList.add(rep);
                        }
                    }
                }
                stats.put("recentReports", reportList);
            } else {
                stats.put("recentReports", Collections.emptyList());
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    /**
     * Danh sách các câu hỏi bị cộng đồng báo lỗi (để Giảng viên/Kiểm duyệt viên rà soát).
     */
    public List<Map<String, Object>> getReportedQuestions(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT q.question_id, q.question_text, q.topic_id, t.topic_name, COUNT(r.rating_id) AS report_count "
                + "FROM questions q "
                + "JOIN topics t ON q.topic_id = t.topic_id "
                + "JOIN question_ratings r ON q.question_id = r.question_id "
                + "WHERE r.rating_type = 'REPORT_ERROR' "
                + "GROUP BY q.question_id, q.question_text, q.topic_id, t.topic_name "
                + "ORDER BY report_count DESC "
                + "LIMIT ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, Math.max(limit, 1));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("questionId", rs.getInt("question_id"));
                    map.put("questionText", rs.getString("question_text"));
                    map.put("topicId", rs.getInt("topic_id"));
                    map.put("topicName", rs.getString("topic_name"));
                    map.put("reportCount", rs.getInt("report_count"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
