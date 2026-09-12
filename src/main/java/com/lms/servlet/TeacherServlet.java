package com.lms.servlet;

import com.google.gson.JsonObject;
import com.lms.dao.QuizDAO;
import com.lms.model.User;
import com.lms.service.AIService;
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

/**
 * Controller cung cấp số liệu thống kê học tập, KPI và Trợ lý AI Co-Pilot
 * dành riêng cho Giảng viên và Quản trị viên (Teacher Dashboard).
 */
@WebServlet(name = "TeacherServlet", urlPatterns = {"/api/teacher/*"})
public class TeacherServlet extends HttpServlet {

    private final QuizDAO quizDAO = new QuizDAO();
    private final AIService aiService = new AIService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        User user = requireTeacherOrAdmin(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/stats")) {
            handleGetStats(resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint GET: " + pathInfo));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        User user = requireTeacherOrAdmin(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if ("/ai/generate".equals(pathInfo)) {
            handleAiGenerateQuestions(req, resp);
        } else if ("/ai/chat".equals(pathInfo)) {
            handleAiTeacherChat(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint POST: " + pathInfo));
        }
    }

    private void handleGetStats(HttpServletResponse resp) throws IOException {
        try {
            Map<String, Object> kpis = quizDAO.getTeacherKPIs();
            Map<String, Integer> misconceptions = quizDAO.getMisconceptionStats();
            List<Map<String, Object>> recentSessions = quizDAO.getRecentSessions(20);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("kpis", kpis);
            responseData.put("misconceptions", misconceptions);
            responseData.put("recentSessions", recentSessions);

            resp.getWriter().write(JsonHelper.success("Dữ liệu thống kê giảng viên", responseData));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi tải dữ liệu thống kê: " + e.getMessage()));
        }
    }

    private void handleAiGenerateQuestions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu yêu cầu không hợp lệ."));
            return;
        }

        String topicName = body.has("topicName") && !body.get("topicName").isJsonNull()
                ? body.get("topicName").getAsString().trim() : "Lập trình Java";
        String difficulty = body.has("difficulty") && !body.get("difficulty").isJsonNull()
                ? body.get("difficulty").getAsString().trim() : "medium";
        String misconceptionTag = body.has("misconceptionTag") && !body.get("misconceptionTag").isJsonNull()
                ? body.get("misconceptionTag").getAsString().trim() : "all";
        int count = body.has("count") && !body.get("count").isJsonNull()
                ? Math.min(Math.max(body.get("count").getAsInt(), 1), 5) : 3;
        String promptHint = body.has("promptHint") && !body.get("promptHint").isJsonNull()
                ? body.get("promptHint").getAsString().trim() : "";

        try {
            List<Map<String, Object>> generatedList = aiService.generateQuestionsForTeacher(
                    topicName, difficulty, misconceptionTag, count, promptHint
            );

            if (generatedList.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                resp.getWriter().write(JsonHelper.error("AI tạm thời không phản hồi hoặc chưa cấu hình API Key. Vui lòng thử lại sau."));
                return;
            }

            resp.getWriter().write(JsonHelper.success("Khởi tạo danh sách câu hỏi bằng AI thành công", generatedList));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi khởi tạo câu hỏi bằng AI: " + e.getMessage()));
        }
    }

    private void handleAiTeacherChat(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("message")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Vui lòng nhập tin nhắn tư vấn."));
            return;
        }

        String userMessage = body.get("message").getAsString().trim();
        String context = body.has("context") && !body.get("context").isJsonNull()
                ? body.get("context").getAsString().trim() : "";

        try {
            String aiAnswer = aiService.teacherChat(userMessage, context);
            Map<String, Object> data = new HashMap<>();
            data.put("response", aiAnswer);
            resp.getWriter().write(JsonHelper.success("Phản hồi từ Trợ Lý Sư Phạm AI", data));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi trò chuyện với AI: " + e.getMessage()));
        }
    }

    private User requireTeacherOrAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để truy cập trang này."));
            return null;
        }

        User user = (User) session.getAttribute("user");
        String role = user.getRole();
        if (role == null || (!role.equalsIgnoreCase("teacher") && !role.equalsIgnoreCase("admin"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(JsonHelper.error("Từ chối truy cập: Trang này chỉ dành cho Giảng viên hoặc Quản trị viên."));
            return null;
        }

        return user;
    }
}
