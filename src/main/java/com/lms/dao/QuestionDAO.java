package com.lms.dao;

import com.lms.model.Question;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object cho bảng [questions].
 * Hỗ trợ: lấy câu hỏi theo topic, tìm theo ID.
 * ★ getExplanation() — AI Fallback Buffer khi Gemini API offline.
 */
public class QuestionDAO {

    /**
     * Lấy tất cả câu hỏi theo chủ đề.
     */
    public List<Question> findByTopicId(int topicId) {
        List<Question> questions = new ArrayList<>();
        String sql = "SELECT * FROM questions WHERE topic_id = ? ORDER BY question_id";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapQuestion(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return questions;
    }

    /**
     * Tìm câu hỏi theo ID.
     */
    public Optional<Question> findById(int questionId) {
        String sql = "SELECT * FROM questions WHERE question_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapQuestion(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * ★ AI Fallback Buffer — Lấy explanation từ DB khi Gemini API không khả dụng.
     * Trả về giải thích cơ bản đã được lưu sẵn trong ngân hàng câu hỏi.
     *
     * @param questionId ID câu hỏi cần lấy giải thích
     * @return Chuỗi explanation hoặc null nếu không có
     */
    public String getExplanation(int questionId) {
        String sql = "SELECT explanation FROM questions WHERE question_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getNString("explanation");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * ★ Batch Fallback — Lấy explanation cho nhiều câu hỏi cùng lúc.
     * @param questionIds Danh sách ID câu hỏi
     * @return Map: questionId → explanation
     */
    public Map<Integer, String> getExplanations(List<Integer> questionIds) {
        Map<Integer, String> result = new HashMap<>();
        if (questionIds == null || questionIds.isEmpty()) return result;

        // Xây dựng IN clause động
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < questionIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }

        String sql = "SELECT question_id, explanation FROM questions WHERE question_id IN (" + placeholders + ")";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < questionIds.size(); i++) {
                ps.setInt(i + 1, questionIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("question_id"), rs.getNString("explanation"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Lấy danh sách tất cả câu hỏi, có thể lọc theo topicId (null hoặc <= 0 để lấy tất cả).
     */
    public List<Question> findAll(Integer topicId) {
        List<Question> questions = new ArrayList<>();
        String sql = (topicId != null && topicId > 0)
                ? "SELECT * FROM questions WHERE topic_id = ? ORDER BY question_id DESC"
                : "SELECT * FROM questions ORDER BY question_id DESC";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (topicId != null && topicId > 0) {
                ps.setInt(1, topicId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapQuestion(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return questions;
    }

    /**
     * Đếm tổng số câu hỏi trong ngân hàng đề.
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM questions";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Tạo câu hỏi mới trong CSDL.
     * @return ID câu hỏi vừa tạo, hoặc -1 nếu thất bại
     */
    public int create(Question q) {
        String sql = "INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty, misconception_tag) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, q.getTopicId());
            ps.setNString(2, q.getQuestionText());
            ps.setNString(3, q.getOptionA());
            ps.setNString(4, q.getOptionB());
            ps.setNString(5, q.getOptionC());
            ps.setNString(6, q.getOptionD());
            ps.setNString(7, q.getCorrectAnswer());
            ps.setNString(8, q.getExplanation());
            ps.setNString(9, q.getDifficulty() != null ? q.getDifficulty() : "medium");
            ps.setNString(10, q.getMisconceptionTag());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Cập nhật thông tin câu hỏi.
     */
    public boolean update(Question q) {
        String sql = "UPDATE questions SET topic_id = ?, question_text = ?, option_a = ?, option_b = ?, "
                   + "option_c = ?, option_d = ?, correct_answer = ?, explanation = ?, difficulty = ?, misconception_tag = ? "
                   + "WHERE question_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, q.getTopicId());
            ps.setNString(2, q.getQuestionText());
            ps.setNString(3, q.getOptionA());
            ps.setNString(4, q.getOptionB());
            ps.setNString(5, q.getOptionC());
            ps.setNString(6, q.getOptionD());
            ps.setNString(7, q.getCorrectAnswer());
            ps.setNString(8, q.getExplanation());
            ps.setNString(9, q.getDifficulty() != null ? q.getDifficulty() : "medium");
            ps.setNString(10, q.getMisconceptionTag());
            ps.setInt(11, q.getQuestionId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Lấy danh sách câu hỏi luyện tập thích ứng theo chủ đề và loại lỗi tư duy.
     * Tự động fallback nếu chủ đề chưa có đủ câu hỏi đúng tag.
     */
    public List<Question> findRemediationQuestions(int topicId, String misconceptionTag, int limit) {
        List<Question> questions = new ArrayList<>();
        int maxLimit = (limit > 0) ? limit : 3;

        // 1. Ưu tiên: Câu hỏi cùng chủ đề + đúng loại lỗi tư duy
        String sql1 = "SELECT TOP (?) * FROM questions WHERE topic_id = ? AND misconception_tag = ? ORDER BY question_id ASC";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql1)) {
            ps.setInt(1, maxLimit);
            ps.setInt(2, topicId);
            ps.setNString(3, misconceptionTag);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapQuestion(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 2. Fallback 1: Nếu chưa đủ, tìm câu hỏi đúng misconception_tag ở các chủ đề khác
        if (questions.size() < maxLimit && misconceptionTag != null) {
            List<Integer> existingIds = new ArrayList<>();
            for (Question q : questions) existingIds.add(q.getQuestionId());

            String sql2 = "SELECT TOP (?) * FROM questions WHERE misconception_tag = ? ORDER BY question_id ASC";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql2)) {
                ps.setInt(1, maxLimit);
                ps.setNString(2, misconceptionTag);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next() && questions.size() < maxLimit) {
                        int qId = rs.getInt("question_id");
                        if (!existingIds.contains(qId)) {
                            questions.add(mapQuestion(rs));
                            existingIds.add(qId);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        // 3. Fallback 2: Nếu vẫn chưa đủ, lấy câu hỏi bất kỳ cùng chủ đề
        if (questions.size() < maxLimit) {
            List<Integer> existingIds = new ArrayList<>();
            for (Question q : questions) existingIds.add(q.getQuestionId());

            String sql3 = "SELECT TOP (?) * FROM questions WHERE topic_id = ? ORDER BY question_id ASC";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql3)) {
                ps.setInt(1, maxLimit);
                ps.setInt(2, topicId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next() && questions.size() < maxLimit) {
                        int qId = rs.getInt("question_id");
                        if (!existingIds.contains(qId)) {
                            questions.add(mapQuestion(rs));
                            existingIds.add(qId);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return questions;
    }

    /**
     * Xóa câu hỏi cùng dữ liệu liên kết (dọn dẹp cascade an toàn trong transaction).
     */
    public boolean delete(int questionId) {
        String deleteLessonsSql = "DELETE FROM remedial_lessons WHERE answer_id IN (SELECT answer_id FROM user_answers WHERE question_id = ?)";
        String deleteAnswersSql = "DELETE FROM user_answers WHERE question_id = ?";
        String deleteQuestionSql = "DELETE FROM questions WHERE question_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(deleteLessonsSql);
                 PreparedStatement ps2 = conn.prepareStatement(deleteAnswersSql);
                 PreparedStatement ps3 = conn.prepareStatement(deleteQuestionSql)) {
                
                ps1.setInt(1, questionId);
                ps1.executeUpdate();

                ps2.setInt(1, questionId);
                ps2.executeUpdate();

                ps3.setInt(1, questionId);
                int affected = ps3.executeUpdate();

                conn.commit();
                return affected > 0;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ── Private Mapper ────────────────────────────────────────────────────

    private Question mapQuestion(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setQuestionId(rs.getInt("question_id"));
        q.setTopicId(rs.getInt("topic_id"));
        q.setQuestionText(rs.getNString("question_text"));
        q.setOptionA(rs.getNString("option_a"));
        q.setOptionB(rs.getNString("option_b"));
        q.setOptionC(rs.getNString("option_c"));
        q.setOptionD(rs.getNString("option_d"));
        String answer = rs.getNString("correct_answer");
        q.setCorrectAnswer(answer != null ? answer.trim() : null);
        q.setExplanation(rs.getNString("explanation"));
        String diff = rs.getNString("difficulty");
        q.setDifficulty(diff != null ? diff.trim() : null);
        try {
            String tag = rs.getNString("misconception_tag");
            q.setMisconceptionTag(tag != null ? tag.trim() : null);
        } catch (SQLException ignored) {}
        Timestamp createdAt = rs.getTimestamp("created_at");
        q.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return q;
    }
}
