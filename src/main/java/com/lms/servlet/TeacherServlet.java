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
    private final com.lms.dao.TopicDAO topicDAO = new com.lms.dao.TopicDAO();
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
        } else if ("/ai/suggest-misconceptions".equals(pathInfo)) {
            handleAiSuggestMisconceptions(req, resp);
        } else if ("/ai/validate-topic".equals(pathInfo)) {
            handleAiValidateTopic(req, resp);
        } else if ("/ai/validate-misconception".equals(pathInfo)) {
            handleAiValidateMisconception(req, resp);
        } else if ("/ai/assist-question".equals(pathInfo)) {
            handleAiAssistQuestion(req, resp);
        } else if ("/ai/validate-key".equals(pathInfo)) {
            handleValidateApiKey(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint POST: " + pathInfo));
        }
    }

    private void handleValidateApiKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        String key = body != null && body.has("apiKey") ? body.get("apiKey").getAsString().trim() : "";
        if (!AIService.isValidApiKey(key)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("API Key không hợp lệ. Vui lòng lấy key từ Google AI Studio (bắt đầu bằng AIzaSy...)."));
            return;
        }

        try {
            com.google.genai.Client testClient = com.google.genai.Client.builder().apiKey(key).build();
            var response = testClient.models.generateContent("gemini-2.0-flash", "Xin chào, phản hồi 'OK' nếu bạn kết nối thành công.", null);
            if (response != null && response.text() != null) {
                HttpSession session = req.getSession(true);
                session.setAttribute("gemini_api_key", key);
                resp.getWriter().write(JsonHelper.success("Kết nối thành công tới Google Gemini 2.0 Flash!", null));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(JsonHelper.error("Google Gemini không trả về dữ liệu. Hãy kiểm tra lại API Key."));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Lỗi xác thực API Key với Google: " + e.getMessage()));
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
        int count = 5;
        if (body.has("count") && !body.get("count").isJsonNull()) {
            try {
                count = Math.min(Math.max(body.get("count").getAsInt(), 1), 50);
            } catch (Exception ignored) {
                count = 5;
            }
        }
        String promptHint = body.has("promptHint") && !body.get("promptHint").isJsonNull()
                ? body.get("promptHint").getAsString().trim() : "";

        String customApiKey = req.getHeader("X-Gemini-Api-Key");
        if (customApiKey == null || customApiKey.isBlank()) {
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute("gemini_api_key") != null) {
                customApiKey = (String) session.getAttribute("gemini_api_key");
            }
        }

        try {
            // Đảm bảo chủ đề luôn tồn tại trong DB để gán topicId hợp lệ
            com.lms.model.Topic topic = topicDAO.findOrCreate(topicName, "Chủ đề mở do người dùng khởi tạo");
            int topicId = topic != null ? topic.getTopicId() : 1;

            List<Map<String, Object>> generatedList = aiService.generateQuestionsForTeacher(
                    topicName, difficulty, misconceptionTag, count, promptHint, customApiKey
            );

            if (generatedList.isEmpty()) {
                // Tự động kích hoạt Fallback Engine nếu vì lý do nào đó danh sách rỗng
                generatedList = FallbackService.generateFallbackQuestions(topicName, difficulty, misconceptionTag, count, promptHint);
            }

            // Gán topicId và topicName vào từng câu hỏi để người dùng có thể lưu ngay vào DB
            for (Map<String, Object> q : generatedList) {
                if (!q.containsKey("topicId") || q.get("topicId") == null) {
                    q.put("topicId", topicId);
                }
                q.put("topicName", topicName);
            }

            resp.getWriter().write(JsonHelper.success("Khởi tạo danh sách câu hỏi bằng AI thành công", generatedList));
        } catch (Exception e) {
            e.printStackTrace();
            // Trong mọi trường hợp ngoại lệ, vẫn đảm bảo trả về bộ câu hỏi chất lượng cao qua Fallback Engine
            List<Map<String, Object>> fallbackList = FallbackService.generateFallbackQuestions(topicName, difficulty, misconceptionTag, count, promptHint);
            resp.getWriter().write(JsonHelper.success("Khởi tạo danh sách câu hỏi thành công", fallbackList));
        }
    }

    private void handleAiSuggestMisconceptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        String topicName = (body != null && body.has("topicName") && !body.get("topicName").isJsonNull())
                ? body.get("topicName").getAsString().trim() : "Kiến thức tổng hợp";

        try {
            List<Map<String, String>> suggestions = aiService.suggestMisconceptions(topicName);
            resp.getWriter().write(JsonHelper.success("Gợi ý bẫy tư duy cho chủ đề: " + topicName, suggestions));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi gợi ý bẫy tư duy: " + e.getMessage()));
        }
    }

    private void handleAiValidateTopic(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        String topicName = (body != null && body.has("topicName") && !body.get("topicName").isJsonNull())
                ? body.get("topicName").getAsString().trim() : "";

        if (topicName.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Vui lòng nhập tên môn học hoặc chủ đề cần thẩm định."));
            return;
        }

        try {
            Map<String, Object> validation = aiService.validateTopic(topicName);
            resp.getWriter().write(JsonHelper.success("Thẩm định môn học / chủ đề thành công", validation));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi thẩm định chủ đề: " + e.getMessage()));
        }
    }

    private void handleAiValidateMisconception(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        String topicName = (body != null && body.has("topicName") && !body.get("topicName").isJsonNull())
                ? body.get("topicName").getAsString().trim() : "Tổng hợp";
        String misconception = (body != null && body.has("misconception") && !body.get("misconception").isJsonNull())
                ? body.get("misconception").getAsString().trim() : "";

        if (misconception.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Vui lòng nhập bẫy tư duy cần thẩm định."));
            return;
        }

        try {
            Map<String, Object> validation = aiService.validateMisconception(topicName, misconception);
            resp.getWriter().write(JsonHelper.success("Thẩm định bẫy tư duy thành công", validation));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi thẩm định bẫy tư duy: " + e.getMessage()));
        }
    }

    private void handleAiAssistQuestion(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("questionPrompt")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Vui lòng nhập nội dung hoặc ý tưởng câu hỏi cần hoàn thiện."));
            return;
        }

        String questionPrompt = body.get("questionPrompt").getAsString().trim();
        String topicName = (body.has("topicName") && !body.get("topicName").isJsonNull())
                ? body.get("topicName").getAsString().trim() : "Kiến thức chung";
        String difficulty = (body.has("difficulty") && !body.get("difficulty").isJsonNull())
                ? body.get("difficulty").getAsString().trim() : "medium";

        try {
            Map<String, Object> assisted = aiService.assistQuestionDraft(topicName, questionPrompt, difficulty);
            resp.getWriter().write(JsonHelper.success("Hoàn thiện câu hỏi bằng AI thành công", assisted));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonHelper.error("Lỗi khi AI hỗ trợ soạn câu hỏi: " + e.getMessage()));
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

        String customApiKey = req.getHeader("X-Gemini-Api-Key");
        if (customApiKey == null || customApiKey.isBlank()) {
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute("gemini_api_key") != null) {
                customApiKey = (String) session.getAttribute("gemini_api_key");
            }
        }

        try {
            String aiAnswer = aiService.teacherChat(userMessage, context, customApiKey);
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
