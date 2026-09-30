package com.lms.dao;

import com.lms.model.CommunityComment;
import com.lms.model.CommunityPost;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object quản lý Diễn đàn & Không gian cộng đồng học tập (Community Forum Feed).
 */
public class CommunityDAO {

    /**
     * Lấy danh sách bài viết theo bộ lọc kênh (channel) hoặc môn học (topicId).
     */
    public List<CommunityPost> listPosts(String channel, Integer topicId, int limit, int offset, Integer currentUserId) throws SQLException {
        List<CommunityPost> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.post_id, p.user_id, p.topic_id, p.channel, p.title, p.content, ")
           .append("       p.likes_count, p.comments_count, p.created_at, p.updated_at, ")
           .append("       u.full_name AS author_name, u.username AS author_username, u.role AS author_role, ")
           .append("       t.topic_name, ")
           .append("       CASE WHEN pl.like_id IS NOT NULL THEN TRUE ELSE FALSE END AS is_liked_by_me ")
           .append("FROM community_posts p ")
           .append("JOIN users u ON p.user_id = u.user_id ")
           .append("LEFT JOIN topics t ON p.topic_id = t.topic_id ")
           .append("LEFT JOIN community_post_likes pl ON p.post_id = pl.post_id AND pl.user_id = ? ")
           .append("WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        params.add(currentUserId != null ? currentUserId : -1);

        if (channel != null && !channel.trim().isEmpty() && !channel.equalsIgnoreCase("all")) {
            sql.append("AND p.channel = ? ");
            params.add(channel.trim().toLowerCase());
        }

        if (topicId != null && topicId > 0) {
            sql.append("AND p.topic_id = ? ");
            params.add(topicId);
        }

        sql.append("ORDER BY p.created_at DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CommunityPost p = mapPostRow(rs);
                    list.add(p);
                }
            }
        }
        return list;
    }

    /**
     * Lấy chi tiết một bài viết theo ID.
     */
    public CommunityPost getPostById(int postId, Integer currentUserId) throws SQLException {
        String sql = "SELECT p.post_id, p.user_id, p.topic_id, p.channel, p.title, p.content, "
                + "       p.likes_count, p.comments_count, p.created_at, p.updated_at, "
                + "       u.full_name AS author_name, u.username AS author_username, u.role AS author_role, "
                + "       t.topic_name, "
                + "       CASE WHEN pl.like_id IS NOT NULL THEN TRUE ELSE FALSE END AS is_liked_by_me "
                + "FROM community_posts p "
                + "JOIN users u ON p.user_id = u.user_id "
                + "LEFT JOIN topics t ON p.topic_id = t.topic_id "
                + "LEFT JOIN community_post_likes pl ON p.post_id = pl.post_id AND pl.user_id = ? "
                + "WHERE p.post_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, currentUserId != null ? currentUserId : -1);
            ps.setInt(2, postId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPostRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tạo một bài viết thảo luận mới.
     */
    public int createPost(int userId, Integer topicId, String channel, String title, String content) throws SQLException {
        String sql = "INSERT INTO community_posts (user_id, topic_id, channel, title, content, likes_count, comments_count, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, userId);
            if (topicId != null && topicId > 0) {
                ps.setInt(2, topicId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, (channel != null && !channel.trim().isEmpty()) ? channel.trim().toLowerCase() : "general");
            ps.setString(4, title);
            ps.setString(5, content);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Xóa bài viết (Chỉ tác giả hoặc Giảng viên/Admin).
     */
    public boolean deletePost(int postId, int userId, boolean isAdminOrTeacher) throws SQLException {
        String sql = isAdminOrTeacher
                ? "DELETE FROM community_posts WHERE post_id = ?"
                : "DELETE FROM community_posts WHERE post_id = ? AND user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, postId);
            if (!isAdminOrTeacher) {
                ps.setInt(2, userId);
            }
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Thả tim / Bỏ thả tim (Toggle Like) bài viết.
     * @return Map chứa { "liked": boolean, "likesCount": int }
     */
    public Map<String, Object> toggleLike(int postId, int userId) throws SQLException {
        Map<String, Object> result = new HashMap<>();

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Kiểm tra đã like chưa
                boolean alreadyLiked = false;
                try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM community_post_likes WHERE post_id = ? AND user_id = ?")) {
                    checkPs.setInt(1, postId);
                    checkPs.setInt(2, userId);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        alreadyLiked = rs.next();
                    }
                }

                if (alreadyLiked) {
                    // Unlike
                    try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM community_post_likes WHERE post_id = ? AND user_id = ?")) {
                        delPs.setInt(1, postId);
                        delPs.setInt(2, userId);
                        delPs.executeUpdate();
                    }
                    try (PreparedStatement decPs = conn.prepareStatement("UPDATE community_posts SET likes_count = GREATEST(likes_count - 1, 0) WHERE post_id = ?")) {
                        decPs.setInt(1, postId);
                        decPs.executeUpdate();
                    }
                    result.put("liked", false);
                } else {
                    // Like
                    try (PreparedStatement insPs = conn.prepareStatement("INSERT INTO community_post_likes (post_id, user_id) VALUES (?, ?)")) {
                        insPs.setInt(1, postId);
                        insPs.setInt(2, userId);
                        insPs.executeUpdate();
                    }
                    try (PreparedStatement incPs = conn.prepareStatement("UPDATE community_posts SET likes_count = likes_count + 1 WHERE post_id = ?")) {
                        incPs.setInt(1, postId);
                        incPs.executeUpdate();
                    }
                    result.put("liked", true);
                }

                // Lấy likesCount mới nhất
                int currentLikes = 0;
                try (PreparedStatement countPs = conn.prepareStatement("SELECT likes_count FROM community_posts WHERE post_id = ?")) {
                    countPs.setInt(1, postId);
                    try (ResultSet rs = countPs.executeQuery()) {
                        if (rs.next()) currentLikes = rs.getInt(1);
                    }
                }

                conn.commit();
                result.put("likesCount", currentLikes);
                return result;

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Lấy danh sách bình luận của bài viết.
     */
    public List<CommunityComment> listComments(int postId) throws SQLException {
        List<CommunityComment> list = new ArrayList<>();
        String sql = "SELECT c.comment_id, c.post_id, c.user_id, c.content, c.created_at, "
                + "       u.full_name AS author_name, u.username AS author_username, u.role AS author_role "
                + "FROM community_post_comments c "
                + "JOIN users u ON c.user_id = u.user_id "
                + "WHERE c.post_id = ? "
                + "ORDER BY c.created_at ASC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CommunityComment c = new CommunityComment();
                    c.setCommentId(rs.getInt("comment_id"));
                    c.setPostId(rs.getInt("post_id"));
                    c.setUserId(rs.getInt("user_id"));
                    c.setContent(rs.getString("content"));
                    c.setCreatedAt(rs.getTimestamp("created_at"));
                    c.setAuthorName(rs.getString("author_name"));
                    c.setAuthorUsername(rs.getString("author_username"));
                    c.setAuthorRole(rs.getString("author_role"));
                    list.add(c);
                }
            }
        }
        return list;
    }

    /**
     * Thêm bình luận vào bài viết.
     */
    public int createComment(int postId, int userId, String content) throws SQLException {
        String sql = "INSERT INTO community_post_comments (post_id, user_id, content, created_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int commentId = 0;
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, postId);
                    ps.setInt(2, userId);
                    ps.setString(3, content);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) commentId = rs.getInt(1);
                    }
                }

                // Tăng comments_count của bài viết
                try (PreparedStatement incPs = conn.prepareStatement("UPDATE community_posts SET comments_count = comments_count + 1 WHERE post_id = ?")) {
                    incPs.setInt(1, postId);
                    incPs.executeUpdate();
                }

                conn.commit();
                return commentId;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Xóa bình luận.
     */
    public boolean deleteComment(int commentId, int postId, int userId, boolean isAdminOrTeacher) throws SQLException {
        String sql = isAdminOrTeacher
                ? "DELETE FROM community_post_comments WHERE comment_id = ?"
                : "DELETE FROM community_post_comments WHERE comment_id = ? AND user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int affected;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, commentId);
                    if (!isAdminOrTeacher) ps.setInt(2, userId);
                    affected = ps.executeUpdate();
                }

                if (affected > 0) {
                    try (PreparedStatement decPs = conn.prepareStatement("UPDATE community_posts SET comments_count = GREATEST(comments_count - 1, 0) WHERE post_id = ?")) {
                        decPs.setInt(1, postId);
                        decPs.executeUpdate();
                    }
                }

                conn.commit();
                return affected > 0;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Thống kê cộng đồng: tổng bài viết, tổng thảo luận, thành viên tích cực.
     */
    public Map<String, Object> getCommunityStats() throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT "
                + "  (SELECT COUNT(*) FROM community_posts) AS total_posts, "
                + "  (SELECT COUNT(*) FROM community_post_comments) AS total_comments, "
                + "  (SELECT COUNT(DISTINCT user_id) FROM community_posts) AS total_creators";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                stats.put("totalPosts", rs.getInt("total_posts"));
                stats.put("totalComments", rs.getInt("total_comments"));
                stats.put("totalCreators", rs.getInt("total_creators"));
            }
        }
        return stats;
    }

    private CommunityPost mapPostRow(ResultSet rs) throws SQLException {
        CommunityPost p = new CommunityPost();
        p.setPostId(rs.getInt("post_id"));
        p.setUserId(rs.getInt("user_id"));
        int topicId = rs.getInt("topic_id");
        p.setTopicId(rs.wasNull() ? null : topicId);
        p.setChannel(rs.getString("channel"));
        p.setTitle(rs.getString("title"));
        p.setContent(rs.getString("content"));
        p.setLikesCount(rs.getInt("likes_count"));
        p.setCommentsCount(rs.getInt("comments_count"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setAuthorName(rs.getString("author_name"));
        p.setAuthorUsername(rs.getString("author_username"));
        p.setAuthorRole(rs.getString("author_role"));
        p.setTopicName(rs.getString("topic_name"));
        p.setLikedByMe(rs.getBoolean("is_liked_by_me"));
        return p;
    }
}
