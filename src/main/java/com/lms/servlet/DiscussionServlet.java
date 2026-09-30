package com.lms.servlet;

import com.google.gson.JsonObject;
import com.lms.dao.DiscussionDAO;
import com.lms.model.QuestionComment;
import com.lms.model.QuestionRating;
import com.lms.model.User;
import com.lms.util.JsonHelper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RESTful Controller cho Diễn đàn thảo luận và Hệ thống tín nhiệm / Báo lỗi câu hỏi.
 * Mọi thành viên (giảng viên, sinh viên, người học tự do) đều có thể trao đổi và đánh giá.
 */
@WebServlet(name = "DiscussionServlet", urlPatterns = {"/api/discussion", "/api/discussion/*"})
public class DiscussionServlet extends HttpServlet {

    private final DiscussionDAO discussionDAO = new DiscussionDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        if (pathInfo.equals("/comments")) {
            handleGetComments(req, resp);
        } else if (pathInfo.equals("/credibility")) {
            handleGetCredibility(req, resp);
        } else if (pathInfo.equals("/reported")) {
            handleGetReportedQuestions(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint GET: " + pathInfo));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        if (pathInfo.equals("/comments")) {
            handleAddComment(req, resp, user);
        } else if (pathInfo.equals("/rate")) {
            handleRateQuestion(req, resp, user);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint POST: " + pathInfo));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith("/comments/")) {
            try {
                int commentId = Integer.parseInt(pathInfo.substring("/comments/".length()));
                boolean isPrivileged = "teacher".equalsIgnoreCase(user.getRole()) || "admin".equalsIgnoreCase(user.getRole());
                boolean ok = discussionDAO.deleteComment(commentId, user.getUserId(), isPrivileged);
                if (ok) {
                    resp.getWriter().write(JsonHelper.success("Đã xóa bình luận thành công!"));
                } else {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write(JsonHelper.error("Không thể xóa bình luận hoặc bạn không có quyền."));
                }
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(JsonHelper.error("Mã bình luận không hợp lệ."));
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint DELETE: " + pathInfo));
        }
    }

    private void handleGetComments(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String qIdStr = req.getParameter("questionId");
        if (qIdStr == null || qIdStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Thiếu tham số questionId."));
            return;
        }

        try {
            int questionId = Integer.parseInt(qIdStr.trim());
            List<QuestionComment> comments = discussionDAO.getCommentsByQuestion(questionId);
            resp.getWriter().write(JsonHelper.success("Danh sách bình luận", comments));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("questionId không hợp lệ."));
        }
    }

    private void handleGetCredibility(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String qIdStr = req.getParameter("questionId");
        if (qIdStr == null || qIdStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Thiếu tham số questionId."));
            return;
        }

        try {
            int questionId = Integer.parseInt(qIdStr.trim());
            Map<String, Object> credibility = discussionDAO.getQuestionCredibility(questionId);

            // Kiểm tra vote của user nếu đã đăng nhập
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute("user") != null) {
                User user = (User) session.getAttribute("user");
                Optional<QuestionRating> userRating = discussionDAO.getUserRating(questionId, user.getUserId());
                credibility.put("userRating", userRating.map(QuestionRating::getRatingType).orElse(null));
                credibility.put("userReportReason", userRating.map(QuestionRating::getReportReason).orElse(null));
            } else {
                credibility.put("userRating", null);
            }

            resp.getWriter().write(JsonHelper.success("Chỉ số độ tin cậy câu hỏi", credibility));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("questionId không hợp lệ."));
        }
    }

    private void handleGetReportedQuestions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int limit = 50;
        String limitStr = req.getParameter("limit");
        if (limitStr != null && !limitStr.isBlank()) {
            try { limit = Integer.parseInt(limitStr.trim()); } catch (NumberFormatException ignored) {}
        }

        List<Map<String, Object>> reported = discussionDAO.getReportedQuestions(limit);
        resp.getWriter().write(JsonHelper.success("Danh sách câu hỏi bị báo lỗi", reported));
    }

    private void handleAddComment(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("questionId") || !body.has("content")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu gửi lên thiếu questionId hoặc content."));
            return;
        }

        int questionId = body.get("questionId").getAsInt();
        String content = body.get("content").getAsString().trim();

        if (content.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Nội dung bình luận không được để trống."));
            return;
        }

        Integer parentCommentId = null;
        if (body.has("parentCommentId") && !body.get("parentCommentId").isJsonNull()) {
            parentCommentId = body.get("parentCommentId").getAsInt();
        }

        QuestionComment comment = new QuestionComment(questionId, user.getUserId(), parentCommentId, content);
        int commentId = discussionDAO.addComment(comment);

        if (commentId > 0) {
            Map<String, Object> data = new HashMap<>();
            data.put("commentId", commentId);
            data.put("userFullName", user.getFullName());
            data.put("username", user.getUsername());
            data.put("userRole", user.getRole());
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write(JsonHelper.success("Đã đăng bình luận thành công!", data));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Không thể lưu bình luận vào cơ sở dữ liệu."));
        }
    }

    private void handleRateQuestion(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("questionId") || !body.has("ratingType")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu gửi lên thiếu questionId hoặc ratingType."));
            return;
        }

        int questionId = body.get("questionId").getAsInt();
        String ratingType = body.get("ratingType").getAsString().trim().toUpperCase();

        if (!"UPVOTE".equals(ratingType) && !"DOWNVOTE".equals(ratingType) && !"REPORT_ERROR".equals(ratingType)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("ratingType chỉ chấp nhận UPVOTE, DOWNVOTE hoặc REPORT_ERROR."));
            return;
        }

        String reportReason = null;
        if (body.has("reportReason") && !body.get("reportReason").isJsonNull()) {
            reportReason = body.get("reportReason").getAsString().trim();
        }

        boolean ok = discussionDAO.rateQuestion(questionId, user.getUserId(), ratingType, reportReason);
        if (ok) {
            Map<String, Object> updatedCredibility = discussionDAO.getQuestionCredibility(questionId);
            resp.getWriter().write(JsonHelper.success("Cập nhật đánh giá câu hỏi thành công!", updatedCredibility));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi lưu đánh giá câu hỏi."));
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để thực hiện thao tác này."));
        return null;
    }
}
