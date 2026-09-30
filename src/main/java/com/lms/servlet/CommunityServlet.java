package com.lms.servlet;

import com.google.gson.JsonObject;
import com.lms.dao.CommunityDAO;
import com.lms.model.CommunityComment;
import com.lms.model.CommunityPost;
import com.lms.model.User;
import com.lms.util.JsonHelper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controller RESTful phục vụ Diễn đàn & Không gian cộng đồng học tập phong cách Discord / Facebook.
 */
@WebServlet(name = "CommunityServlet", urlPatterns = {"/api/community", "/api/community/*"})
public class CommunityServlet extends HttpServlet {

    private final CommunityDAO communityDAO = new CommunityDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        User currentUser = getOptionalUser(req);
        Integer currentUserId = currentUser != null ? currentUser.getUserId() : null;

        try {
            if (pathInfo.equals("/posts") || pathInfo.isEmpty()) {
                // GET /api/community/posts
                String channel = req.getParameter("channel");
                String topicIdStr = req.getParameter("topicId");
                Integer topicId = null;
                if (topicIdStr != null && !topicIdStr.trim().isEmpty()) {
                    try { topicId = Integer.parseInt(topicIdStr.trim()); } catch (NumberFormatException ignored) {}
                }

                int limit = 30;
                try {
                    String limitStr = req.getParameter("limit");
                    if (limitStr != null) limit = Math.min(100, Math.max(1, Integer.parseInt(limitStr)));
                } catch (NumberFormatException ignored) {}

                int offset = 0;
                try {
                    String offsetStr = req.getParameter("offset");
                    if (offsetStr != null) offset = Math.max(0, Integer.parseInt(offsetStr));
                } catch (NumberFormatException ignored) {}

                List<CommunityPost> posts = communityDAO.listPosts(channel, topicId, limit, offset, currentUserId);
                resp.getWriter().write(JsonHelper.success(posts));

            } else if (pathInfo.startsWith("/posts/")) {
                String sub = pathInfo.substring("/posts/".length());
                if (sub.contains("/comments")) {
                    // GET /api/community/posts/{id}/comments
                    int postId = Integer.parseInt(sub.split("/")[0]);
                    List<CommunityComment> comments = communityDAO.listComments(postId);
                    resp.getWriter().write(JsonHelper.success(comments));
                } else {
                    // GET /api/community/posts/{id}
                    int postId = Integer.parseInt(sub);
                    CommunityPost post = communityDAO.getPostById(postId, currentUserId);
                    if (post != null) {
                        resp.getWriter().write(JsonHelper.success(post));
                    } else {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        resp.getWriter().write(JsonHelper.error("Không tìm thấy bài viết #" + postId));
                    }
                }

            } else if (pathInfo.equals("/stats")) {
                // GET /api/community/stats
                Map<String, Object> stats = communityDAO.getCommunityStats();
                resp.getWriter().write(JsonHelper.success(stats));

            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Endpoint GET không hợp lệ: " + pathInfo));
            }
        } catch (Exception ex) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi máy chủ: " + ex.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            if (pathInfo.equals("/posts") || pathInfo.isEmpty()) {
                // POST /api/community/posts
                JsonObject body = JsonHelper.parseRequestBody(req);
                String title = body.has("title") ? body.get("title").getAsString().trim() : "";
                String content = body.has("content") ? body.get("content").getAsString().trim() : "";
                String channel = body.has("channel") ? body.get("channel").getAsString().trim() : "general";
                Integer topicId = (body.has("topicId") && !body.get("topicId").isJsonNull()) ? body.get("topicId").getAsInt() : null;

                if (title.isEmpty() || content.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write(JsonHelper.error("Tiêu đề và nội dung bài viết không được để trống."));
                    return;
                }

                int postId = communityDAO.createPost(user.getUserId(), topicId, channel, title, content);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(JsonHelper.success(Map.of(
                        "postId", postId,
                        "message", "Đăng bài viết lên diễn đàn thành công!"
                )));

            } else if (pathInfo.startsWith("/posts/") && pathInfo.endsWith("/like")) {
                // POST /api/community/posts/{id}/like
                String idStr = pathInfo.substring("/posts/".length(), pathInfo.length() - "/like".length());
                int postId = Integer.parseInt(idStr);
                Map<String, Object> result = communityDAO.toggleLike(postId, user.getUserId());
                resp.getWriter().write(JsonHelper.success(result));

            } else if (pathInfo.startsWith("/posts/") && pathInfo.endsWith("/comments")) {
                // POST /api/community/posts/{id}/comments
                String idStr = pathInfo.substring("/posts/".length(), pathInfo.length() - "/comments".length());
                int postId = Integer.parseInt(idStr);
                JsonObject body = JsonHelper.parseRequestBody(req);
                String content = body.has("content") ? body.get("content").getAsString().trim() : "";

                if (content.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write(JsonHelper.error("Nội dung bình luận không được để trống."));
                    return;
                }

                int commentId = communityDAO.createComment(postId, user.getUserId(), content);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(JsonHelper.success(Map.of(
                        "commentId", commentId,
                        "message", "Đã gửi bình luận thành công!"
                )));

            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Endpoint POST không hợp lệ: " + pathInfo));
            }
        } catch (Exception ex) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi xử lý dữ liệu: " + ex.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        boolean isTeacherOrAdmin = user.getRole() != null &&
                (user.getRole().equalsIgnoreCase("teacher") || user.getRole().equalsIgnoreCase("admin"));

        try {
            if (pathInfo.startsWith("/posts/")) {
                String sub = pathInfo.substring("/posts/".length());
                if (sub.contains("/comments/")) {
                    // DELETE /api/community/posts/{postId}/comments/{commentId}
                    String[] parts = sub.split("/comments/");
                    int postId = Integer.parseInt(parts[0]);
                    int commentId = Integer.parseInt(parts[1]);
                    boolean ok = communityDAO.deleteComment(commentId, postId, user.getUserId(), isTeacherOrAdmin);
                    if (ok) {
                        resp.getWriter().write(JsonHelper.success(Map.of("message", "Đã xóa bình luận.")));
                    } else {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        resp.getWriter().write(JsonHelper.error("Bạn không có quyền xóa bình luận này."));
                    }
                } else {
                    // DELETE /api/community/posts/{id}
                    int postId = Integer.parseInt(sub);
                    boolean ok = communityDAO.deletePost(postId, user.getUserId());
                    if (ok) {
                        resp.getWriter().write(JsonHelper.success(Map.of("message", "Đã xóa bài viết của bạn khỏi diễn đàn.")));
                    } else {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        resp.getWriter().write(JsonHelper.error("Bạn chỉ có thể xóa bài viết do chính bạn đăng tải."));
                    }
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Endpoint DELETE không hợp lệ."));
            }
        } catch (Exception ex) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi xóa: " + ex.getMessage()));
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để thực hiện tương tác này trên diễn đàn!"));
            return null;
        }
        return (User) session.getAttribute("user");
    }

    private User getOptionalUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        return null;
    }
}
