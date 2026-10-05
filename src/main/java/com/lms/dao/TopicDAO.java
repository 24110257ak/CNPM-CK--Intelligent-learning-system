package com.lms.dao;

import com.lms.model.Topic;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object cho bảng [topics].
 * Hỗ trợ: lấy danh sách chủ đề, tìm theo ID.
 */
public class TopicDAO {

    /**
     * Lấy tất cả chủ đề, sắp xếp theo display_order.
     */
    public List<Topic> findAll() {
        List<Topic> topics = new ArrayList<>();
        String sql = "SELECT * FROM topics ORDER BY display_order ASC";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                topics.add(mapTopic(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topics;
    }

    /**
     * Tìm chủ đề theo ID.
     */
    public Optional<Topic> findById(int topicId) {
        String sql = "SELECT * FROM topics WHERE topic_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTopic(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Tìm chủ đề theo tên (case-insensitive).
     */
    public Optional<Topic> findByName(String topicName) {
        if (topicName == null || topicName.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM topics WHERE LOWER(topic_name) = LOWER(?) LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topicName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTopic(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Tạo chủ đề / môn học mới.
     */
    public Topic create(Topic topic) {
        String sql = "INSERT INTO topics (topic_name, description, parent_topic_id, display_order) "
                   + "VALUES (?, ?, ?, ?) RETURNING topic_id, created_at";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic.getTopicName().trim());
            ps.setString(2, topic.getDescription());
            if (topic.getParentTopicId() != null) {
                ps.setInt(3, topic.getParentTopicId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setInt(4, topic.getDisplayOrder() > 0 ? topic.getDisplayOrder() : 99);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    topic.setTopicId(rs.getInt("topic_id"));
                    Timestamp ca = rs.getTimestamp("created_at");
                    topic.setCreatedAt(ca != null ? ca.toLocalDateTime() : null);
                    return topic;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topic;
    }

    /**
     * Tìm chủ đề theo tên hoặc tự động tạo mới nếu chưa tồn tại.
     */
    public Topic findOrCreate(String topicName, String description) {
        Optional<Topic> existing = findByName(topicName);
        if (existing.isPresent()) {
            return existing.get();
        }
        Topic newTopic = new Topic();
        newTopic.setTopicName(topicName.trim());
        newTopic.setDescription(description != null ? description : "Chủ đề do người dùng khởi tạo");
        newTopic.setDisplayOrder(99);
        return create(newTopic);
    }

    /**
     * Đếm số câu hỏi thuộc về một chủ đề.
     */
    public int countQuestions(int topicId) {
        String sql = "SELECT COUNT(*) FROM questions WHERE topic_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Cập nhật thông tin chủ đề / môn học.
     */
    public boolean update(Topic topic) {
        String sql = "UPDATE topics SET topic_name = ?, description = ?, display_order = ? WHERE topic_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic.getTopicName().trim());
            ps.setString(2, topic.getDescription());
            ps.setInt(3, topic.getDisplayOrder() > 0 ? topic.getDisplayOrder() : 99);
            ps.setInt(4, topic.getTopicId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Xóa chủ đề và các dữ liệu phụ thuộc một cách an toàn trong Transaction.
     */
    public boolean delete(int topicId) {
        String sqlNullParent = "UPDATE topics SET parent_topic_id = NULL WHERE parent_topic_id = ?";
        String sqlUserAnswers = "DELETE FROM user_answers WHERE session_id IN (SELECT session_id FROM quiz_sessions WHERE topic_id = ?) "
                              + "OR question_id IN (SELECT question_id FROM questions WHERE topic_id = ?)";
        String sqlSessions = "DELETE FROM quiz_sessions WHERE topic_id = ?";
        String sqlRemedial = "DELETE FROM remedial_lessons WHERE question_id IN (SELECT question_id FROM questions WHERE topic_id = ?)";
        String sqlRatings = "DELETE FROM question_ratings WHERE question_id IN (SELECT question_id FROM questions WHERE topic_id = ?)";
        String sqlComments = "DELETE FROM question_comments WHERE question_id IN (SELECT question_id FROM questions WHERE topic_id = ?)";
        String sqlQuestions = "DELETE FROM questions WHERE topic_id = ?";
        String sqlTopic = "DELETE FROM topics WHERE topic_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlNullParent)) {
                    ps.setInt(1, topicId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlUserAnswers)) {
                    ps.setInt(1, topicId);
                    ps.setInt(2, topicId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlSessions)) {
                    ps.setInt(1, topicId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlRemedial)) {
                    ps.setInt(1, topicId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlRatings)) {
                    ps.setInt(1, topicId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlComments)) {
                    ps.setInt(1, topicId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlQuestions)) {
                    ps.setInt(1, topicId);
                    ps.executeUpdate();
                }
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sqlTopic)) {
                    ps.setInt(1, topicId);
                    rows = ps.executeUpdate();
                }
                conn.commit();
                return rows > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ── Private Mapper ────────────────────────────────────────────────────

    private Topic mapTopic(ResultSet rs) throws SQLException {
        Topic t = new Topic();
        t.setTopicId(rs.getInt("topic_id"));
        t.setTopicName(rs.getString("topic_name"));
        t.setDescription(rs.getString("description"));
        int parentId = rs.getInt("parent_topic_id");
        t.setParentTopicId(rs.wasNull() ? null : parentId);
        t.setDisplayOrder(rs.getInt("display_order"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        t.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return t;
    }
}
