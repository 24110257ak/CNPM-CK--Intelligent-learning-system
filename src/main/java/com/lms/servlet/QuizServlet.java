package com.lms.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lms.dao.QuestionDAO;
import com.lms.model.Question;
import com.lms.model.QuizSession;
import com.lms.model.User;
import com.lms.service.QuizService;
import com.lms.util.JsonHelper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.*;

/**
 * Controller điều phối quá trình làm bài trắc nghiệm, nộp bài, chấm điểm,
 * hiển thị bài học củng cố kiến thức và bài tập hồi quy thích ứng (Adaptive Remediation).
 */
@WebServlet(name = "QuizServlet", urlPatterns = {"/api/quiz/*", "/api/remediation", "/api/remediation/*"})
public class QuizServlet extends HttpServlet {

    private final QuizService quizService = new QuizService();
    private final QuestionDAO questionDAO = new QuestionDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String fullPath = req.getServletPath() + (req.getPathInfo() != null ? req.getPathInfo() : "");

        if (fullPath.equals("/api/quiz/start")) {
            handleStartQuiz(req, resp, user);
        } else if (fullPath.equals("/api/quiz/submit")) {
            handleSubmitQuiz(req, resp, user);
        } else if (fullPath.equals("/api/remediation/submit") || fullPath.equals("/api/quiz/remediation/submit")) {
            handleSubmitRemediation(req, resp, user);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint POST: " + fullPath));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User user = getAuthenticatedUser(req, resp);
        if (user == null) return;

        String fullPath = req.getServletPath() + (req.getPathInfo() != null ? req.getPathInfo() : "");

        if (fullPath.startsWith("/api/remediation") || fullPath.equals("/api/quiz/remediation")) {
            handleGetRemediation(req, resp, user);
        } else if (fullPath.equals("/api/quiz/history")) {
            handleGetHistory(resp, user);
        } else if (fullPath.startsWith("/api/quiz/session/")) {
            String sessionIdStr = fullPath.substring("/api/quiz/session/".length());
            handleGetSessionDetail(sessionIdStr, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonHelper.error("Không tìm thấy endpoint GET: " + fullPath));
        }
    }

    private void handleStartQuiz(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("topicId")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Thiếu mã chủ đề (topicId)."));
            return;
        }

        int topicId = body.get("topicId").getAsInt();

        try {
            Map<String, Object> quizData = quizService.startQuiz(user.getUserId(), topicId);
            resp.getWriter().write(JsonHelper.success("Bắt đầu bài kiểm tra", quizData));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error(e.getMessage()));
        }
    }

    private void handleSubmitQuiz(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("sessionId") || !body.has("answers")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu nộp bài không đầy đủ (cần sessionId và danh sách answers)."));
            return;
        }

        int sessionId = body.get("sessionId").getAsInt();
        JsonArray answersArray = body.getAsJsonArray("answers");

        List<Map<String, Object>> answersList = new ArrayList<>();
        for (JsonElement el : answersArray) {
            if (el.isJsonObject()) {
                JsonObject ansObj = el.getAsJsonObject();
                Map<String, Object> map = new HashMap<>();
                map.put("questionId", ansObj.get("questionId").getAsInt());
                map.put("userAnswer", ansObj.has("userAnswer") && !ansObj.get("userAnswer").isJsonNull()
                        ? ansObj.get("userAnswer").getAsString() : "");
                map.put("confidenceLevel", ansObj.has("confidenceLevel") && !ansObj.get("confidenceLevel").isJsonNull()
                        ? ansObj.get("confidenceLevel").getAsString() : "CERTAIN");
                answersList.add(map);
            }
        }

        Map<String, Object> submissionResult = quizService.submitQuiz(sessionId, user.getUserId(), answersList);
        resp.getWriter().write(JsonHelper.success("Chấm điểm và phân tích hoàn tất", submissionResult));
    }

    /**
     * Lấy 2-3 câu hỏi ôn tập tương ứng với lỗ hổng tư duy (Adaptive Remediation).
     */
    private void handleGetRemediation(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String topicParam = req.getParameter("topicId");
        String misconception = req.getParameter("misconception");

        int topicId = 1;
        if (topicParam != null && !topicParam.trim().isEmpty()) {
            try {
                topicId = Integer.parseInt(topicParam.trim());
            } catch (NumberFormatException ignored) {}
        }

        List<Question> questions = questionDAO.findRemediationQuestions(topicId, misconception, 3);

        // Che đáp án đúng và giải thích trước khi gửi về client
        List<Map<String, Object>> safeQuestions = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> item = new HashMap<>();
            item.put("questionId", q.getQuestionId());
            item.put("topicId", q.getTopicId());
            item.put("questionText", q.getQuestionText());
            item.put("optionA", q.getOptionA());
            item.put("optionB", q.getOptionB());
            item.put("optionC", q.getOptionC());
            item.put("optionD", q.getOptionD());
            item.put("difficulty", q.getDifficulty());
            item.put("misconceptionTag", q.getMisconceptionTag());
            safeQuestions.add(item);
        }

        resp.getWriter().write(JsonHelper.success("Danh sách bài tập khắc phục lỗ hổng tư duy", safeQuestions));
    }

    /**
     * Nộp bài làm mini-quiz khắc phục lỗ hổng tư duy (Adaptive Remediation Submit).
     */
    private void handleSubmitRemediation(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("answers")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Dữ liệu nộp bài phục hồi không hợp lệ."));
            return;
        }

        int sessionId = body.has("sessionId") ? body.get("sessionId").getAsInt() : 0;
        String misconception = body.has("misconception") ? body.get("misconception").getAsString() : "general";
        JsonArray answersArray = body.getAsJsonArray("answers");

        int correctCount = 0;
        int total = answersArray.size();
        List<Map<String, Object>> feedbackList = new ArrayList<>();

        for (JsonElement el : answersArray) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                int qId = obj.get("questionId").getAsInt();
                String chosen = obj.has("userAnswer") && !obj.get("userAnswer").isJsonNull()
                        ? obj.get("userAnswer").getAsString().trim().toUpperCase() : "";

                Optional<Question> qOpt = questionDAO.findById(qId);
                boolean isCorrect = false;
                String realAnswer = "";
                String expl = "";

                if (qOpt.isPresent()) {
                    Question q = qOpt.get();
                    realAnswer = q.getCorrectAnswer();
                    expl = q.getExplanation();
                    if (realAnswer != null && realAnswer.trim().equalsIgnoreCase(chosen)) {
                        isCorrect = true;
                        correctCount++;
                    }
                }

                Map<String, Object> fb = new HashMap<>();
                fb.put("questionId", qId);
                fb.put("userAnswer", chosen);
                fb.put("correctAnswer", realAnswer);
                fb.put("isCorrect", isCorrect);
                fb.put("explanation", expl);
                feedbackList.add(fb);
            }
        }

        boolean allCorrect = (total > 0 && correctCount == total);
        boolean repaired = allCorrect || (total >= 3 && correctCount >= 2);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("misconception", misconception);
        result.put("total", total);
        result.put("correctCount", correctCount);
        result.put("allCorrect", allCorrect);
        result.put("repaired", repaired);
        result.put("feedback", feedbackList);

        resp.getWriter().write(JsonHelper.success("Đã chấm điểm bài tập phục hồi kiến thức", result));
    }

    private void handleGetHistory(HttpServletResponse resp, User user) throws IOException {
        List<QuizSession> history = quizService.getUserHistory(user.getUserId());
        resp.getWriter().write(JsonHelper.success("Lịch sử làm bài", history));
    }

    private void handleGetSessionDetail(String sessionIdStr, HttpServletResponse resp) throws IOException {
        try {
            int sessionId = Integer.parseInt(sessionIdStr);
            Map<String, Object> details = quizService.getSessionDetails(sessionId);
            resp.getWriter().write(JsonHelper.success("Chi tiết phiên làm bài", details));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Mã phiên không hợp lệ."));
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
