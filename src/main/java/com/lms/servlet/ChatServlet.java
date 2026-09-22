package com.lms.servlet;

import com.google.gson.JsonObject;
import com.lms.dao.ChatDAO;
import com.lms.dao.QuizDAO;
import com.lms.model.ChatMessage;
import com.lms.model.User;
import com.lms.service.AIService;
import com.lms.util.JsonHelper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

/**
 * Controller cho Chatbot trợ giảng AI đa nhân cách (Senior Dev, Peer Tutor, Professor).
 */
@WebServlet(name = "ChatServlet", urlPatterns = {"/api/chat/*"})
public class ChatServlet extends HttpServlet {

    private final ChatDAO chatDAO = new ChatDAO();
    private final QuizDAO quizDAO = new QuizDAO();
    private final AIService aiService = new AIService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();

        if (pathInfo == null || !"/send".equals(pathInfo)) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint."));
            return;
        }

        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("message")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Vui lòng nhập tin nhắn."));
            return;
        }

        String userMessage = body.get("message").getAsString().trim();
        if (userMessage.length() > 4000) {
            resp.setStatus(422); // Unprocessable Entity
            resp.getWriter().write(JsonHelper.error("Tin nhắn quá dài (tối đa 4000 ký tự)."));
            return;
        }
        if (userMessage.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Tin nhắn không được để trống."));
            return;
        }

        String persona = body.has("persona") ? body.get("persona").getAsString() : "peer_tutor";
        if (!persona.equals("senior_dev") && !persona.equals("peer_tutor") && !persona.equals("professor")) persona = "peer_tutor";
        Integer sessionId = body.has("sessionId") && !body.get("sessionId").isJsonNull() ? body.get("sessionId").getAsInt() : null;
        if (sessionId != null && quizDAO.findSessionForUser(sessionId, user.getUserId()) == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(JsonHelper.error("Phiên làm bài đính kèm không thuộc tài khoản hiện tại."));
            return;
        }
        String context = body.has("context") && !body.get("context").isJsonNull() ? body.get("context").getAsString() : "";

        // Gọi AI tạo phản hồi
        String aiResponse = aiService.chat(userMessage, persona, context);

        // Lưu vào CSDL
        ChatMessage msg = new ChatMessage();
        msg.setUserId(user.getUserId());
        msg.setSessionId(sessionId);
        msg.setUserMessage(userMessage);
        msg.setAiResponse(aiResponse);
        msg.setPersona(persona);
        chatDAO.saveMessage(msg);

        JsonObject data = new JsonObject();
        data.addProperty("userMessage", userMessage);
        data.addProperty("aiResponse", aiResponse);
        data.addProperty("persona", persona);
        data.addProperty("sessionId", sessionId);

        resp.getWriter().write(JsonHelper.success("AI phản hồi thành công", data));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();

        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        if (pathInfo == null || "/history".equals(pathInfo)) {
            String limitParam = req.getParameter("limit");
            int limit = 30;
            if (limitParam != null) {
                try {
                    limit = Integer.parseInt(limitParam);
                } catch (NumberFormatException ignored) {}
            }

            List<ChatMessage> history = chatDAO.getRecentByUser(user.getUserId(), limit);
            resp.getWriter().write(JsonHelper.success("Lịch sử tin nhắn", history));
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint."));
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.getWriter().write(JsonHelper.error("Chưa đăng nhập."));
        return null;
    }
}
