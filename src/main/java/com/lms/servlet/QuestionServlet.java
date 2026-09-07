package com.lms.servlet;

import com.google.gson.JsonObject;
import com.lms.dao.QuestionDAO;
import com.lms.model.Question;
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
 * RESTful Controller quản lý ngân hàng câu hỏi (CRUD questions).
 * Phân quyền: Giảng viên (teacher) và Quản trị viên (admin).
 */
@WebServlet(name = "QuestionServlet", urlPatterns = {"/api/questions", "/api/questions/*"})
public class QuestionServlet extends HttpServlet {

    private final QuestionDAO questionDAO = new QuestionDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();

        if (pathInfo == null || "/".equals(pathInfo)) {
            // Lấy danh sách câu hỏi (hỗ trợ lọc theo topicId)
            String topicParam = req.getParameter("topicId");
            Integer topicId = null;
            if (topicParam != null && !topicParam.trim().isEmpty()) {
                try {
                    topicId = Integer.parseInt(topicParam.trim());
                } catch (NumberFormatException ignored) {}
            }
            List<Question> list = questionDAO.findAll(topicId);
            resp.getWriter().write(JsonHelper.success("Lấy danh sách câu hỏi thành công", list));
        } else {
            // Lấy chi tiết 1 câu hỏi theo ID
            try {
                int questionId = Integer.parseInt(pathInfo.substring(1));
                Optional<Question> qOpt = questionDAO.findById(questionId);
                if (qOpt.isPresent()) {
                    resp.getWriter().write(JsonHelper.success("Chi tiết câu hỏi", qOpt.get()));
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write(JsonHelper.error("Không tìm thấy câu hỏi ID: " + questionId));
                }
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(JsonHelper.error("Mã câu hỏi không hợp lệ."));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = requireTeacherOrAdmin(req, resp);
        if (user == null) return;

        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu JSON không hợp lệ."));
            return;
        }

        // Validate các trường bắt buộc
        if (!body.has("topicId") || !body.has("questionText") ||
            !body.has("optionA") || !body.has("optionB") ||
            !body.has("optionC") || !body.has("optionD") ||
            !body.has("correctAnswer")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Vui lòng điền đầy đủ chủ đề, nội dung câu hỏi, 4 đáp án và đáp án đúng."));
            return;
        }

        String correctAnswer = body.get("correctAnswer").getAsString().trim().toUpperCase();
        if (!correctAnswer.matches("^[A-D]$")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Đáp án đúng phải là một trong các chữ cái A, B, C, D."));
            return;
        }

        Question q = new Question();
        q.setTopicId(body.get("topicId").getAsInt());
        q.setQuestionText(body.get("questionText").getAsString().trim());
        q.setOptionA(body.get("optionA").getAsString().trim());
        q.setOptionB(body.get("optionB").getAsString().trim());
        q.setOptionC(body.get("optionC").getAsString().trim());
        q.setOptionD(body.get("optionD").getAsString().trim());
        q.setCorrectAnswer(correctAnswer);

        if (body.has("explanation") && !body.get("explanation").isJsonNull()) {
            q.setExplanation(body.get("explanation").getAsString().trim());
        }
        if (body.has("difficulty") && !body.get("difficulty").isJsonNull()) {
            q.setDifficulty(body.get("difficulty").getAsString().trim().toLowerCase());
        } else {
            q.setDifficulty("medium");
        }
        if (body.has("misconceptionTag") && !body.get("misconceptionTag").isJsonNull()) {
            q.setMisconceptionTag(body.get("misconceptionTag").getAsString().trim());
        }

        int createdId = questionDAO.create(q);
        if (createdId > 0) {
            Map<String, Object> result = new HashMap<>();
            result.put("questionId", createdId);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write(JsonHelper.success("Tạo câu hỏi mới thành công!", result));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Không thể lưu câu hỏi vào cơ sở dữ liệu."));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = requireTeacherOrAdmin(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu JSON không hợp lệ."));
            return;
        }

        int questionId = -1;
        if (pathInfo != null && pathInfo.length() > 1) {
            try {
                questionId = Integer.parseInt(pathInfo.substring(1));
            } catch (NumberFormatException ignored) {}
        }
        if (questionId <= 0 && body.has("questionId")) {
            questionId = body.get("questionId").getAsInt();
        }

        if (questionId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Mã câu hỏi (questionId) không hợp lệ."));
            return;
        }

        Optional<Question> existingOpt = questionDAO.findById(questionId);
        if (existingOpt.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy câu hỏi ID: " + questionId));
            return;
        }

        Question q = existingOpt.get();
        if (body.has("topicId")) q.setTopicId(body.get("topicId").getAsInt());
        if (body.has("questionText")) q.setQuestionText(body.get("questionText").getAsString().trim());
        if (body.has("optionA")) q.setOptionA(body.get("optionA").getAsString().trim());
        if (body.has("optionB")) q.setOptionB(body.get("optionB").getAsString().trim());
        if (body.has("optionC")) q.setOptionC(body.get("optionC").getAsString().trim());
        if (body.has("optionD")) q.setOptionD(body.get("optionD").getAsString().trim());
        if (body.has("correctAnswer")) {
            String ca = body.get("correctAnswer").getAsString().trim().toUpperCase();
            if (ca.matches("^[A-D]$")) {
                q.setCorrectAnswer(ca);
            }
        }
        if (body.has("explanation")) q.setExplanation(body.get("explanation").getAsString().trim());
        if (body.has("difficulty")) q.setDifficulty(body.get("difficulty").getAsString().trim().toLowerCase());
        if (body.has("misconceptionTag")) {
            q.setMisconceptionTag(body.get("misconceptionTag").isJsonNull() ? null : body.get("misconceptionTag").getAsString().trim());
        }

        boolean ok = questionDAO.update(q);
        if (ok) {
            resp.getWriter().write(JsonHelper.success("Cập nhật câu hỏi thành công!", q));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi cập nhật câu hỏi."));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = requireTeacherOrAdmin(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Thiếu mã câu hỏi cần xóa trên đường dẫn URL."));
            return;
        }

        try {
            int questionId = Integer.parseInt(pathInfo.substring(1));
            boolean ok = questionDAO.delete(questionId);
            if (ok) {
                resp.getWriter().write(JsonHelper.success("Đã xóa câu hỏi ID " + questionId + " thành công!"));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Không tìm thấy câu hỏi hoặc không thể xóa."));
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Mã câu hỏi không hợp lệ."));
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để thực hiện chức năng này."));
        return null;
    }

    private User requireTeacherOrAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return null;

        String role = user.getRole();
        if (role == null || (!role.equalsIgnoreCase("teacher") && !role.equalsIgnoreCase("admin"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(JsonHelper.error("Từ chối truy cập: Quyền hạn của bạn không đủ để thực hiện thao tác này."));
            return null;
        }
        return user;
    }
}
