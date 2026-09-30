package com.lms.service;

import com.lms.dao.QuestionDAO;
import com.lms.dao.QuizDAO;
import com.lms.dao.TopicDAO;
import com.lms.dao.UserDAO;
import com.lms.model.Question;
import com.lms.model.QuizSession;
import com.lms.model.RemedialLesson;
import com.lms.model.User;
import com.lms.model.UserAnswer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service quản lý toàn bộ chu trình thi trắc nghiệm và kích hoạt AI củng cố kiến thức.
 */
public class QuizService {

    private final QuizDAO quizDAO;
    private final QuestionDAO questionDAO;
    private final TopicDAO topicDAO;
    private final UserDAO userDAO;
    private final AIService aiService;

    public QuizService() {
        this.quizDAO = new QuizDAO();
        this.questionDAO = new QuestionDAO();
        this.topicDAO = new TopicDAO();
        this.userDAO = new UserDAO();
        this.aiService = new AIService();
    }

    public QuizService(QuizDAO quizDAO, QuestionDAO questionDAO, TopicDAO topicDAO, UserDAO userDAO, AIService aiService) {
        this.quizDAO = quizDAO;
        this.questionDAO = questionDAO;
        this.topicDAO = topicDAO;
        this.userDAO = userDAO;
        this.aiService = aiService;
    }

    /**
     * Bắt đầu một bài trắc nghiệm mới theo chủ đề.
     * Trả về session và danh sách câu hỏi đã ẩn đáp án đúng.
     */
    public Map<String, Object> startQuiz(int userId, int topicId) {
        List<Question> rawQuestions = questionDAO.findByTopicId(topicId);
        if (rawQuestions.isEmpty()) {
            throw new IllegalArgumentException("Chủ đề này chưa có câu hỏi trắc nghiệm.");
        }

        // Tạo phiên làm bài mới trong DB
        QuizSession session = new QuizSession();
        session.setUserId(userId);
        session.setTopicId(topicId);
        session.setTotalQuestions(rawQuestions.size());

        int sessionId = quizDAO.createSession(session);
        session.setSessionId(sessionId);

        // Ẩn đáp án đúng và lời giải khi gửi về Client (chống lộ đề qua Inspect Network)
        List<Map<String, Object>> safeQuestions = new ArrayList<>();
        for (Question q : rawQuestions) {
            Map<String, Object> map = new HashMap<>();
            map.put("questionId", q.getQuestionId());
            map.put("topicId", q.getTopicId());
            map.put("questionText", q.getQuestionText());
            map.put("optionA", q.getOptionA());
            map.put("optionB", q.getOptionB());
            map.put("optionC", q.getOptionC());
            map.put("optionD", q.getOptionD());
            map.put("difficulty", q.getDifficulty());
            safeQuestions.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("topicId", topicId);
        result.put("totalQuestions", rawQuestions.size());
        result.put("questions", safeQuestions);
        return result;
    }

    /**
     * Nộp bài, chấm điểm, ghi nhận Confidence Tagging và sinh bài học AI.
     */
    public Map<String, Object> submitQuiz(int sessionId, int userId, List<Map<String, Object>> submittedAnswers) {
        QuizSession ownedSession = quizDAO.findSessionForUser(sessionId, userId);
        if (ownedSession == null) throw new SecurityException("Phiên làm bài không tồn tại hoặc không thuộc tài khoản hiện tại.");
        if (ownedSession.getCompletedAt() != null || quizDAO.countAnswersBySession(sessionId) > 0) {
            // Idempotency: Phiên làm bài này đã được nộp hoặc đã chấm điểm trước đó.
            // Tự động trả về kết quả đã chấm thay vì ném lỗi khiến học sinh bị kẹt giao diện.
            return buildExistingSubmissionResult(ownedSession, userId);
        }
        if (submittedAnswers == null || submittedAnswers.isEmpty())
            throw new IllegalArgumentException("Danh sách câu trả lời không được để trống.");
        if (submittedAnswers.size() != ownedSession.getTotalQuestions())
            throw new IllegalArgumentException("Số câu trả lời không khớp với bài kiểm tra.");

        Set<Integer> uniqueQuestionIds = new HashSet<>();
        for (Map<String,Object> answer : submittedAnswers) {
            Object idValue = answer.get("questionId");
            if (!(idValue instanceof Number)) throw new IllegalArgumentException("questionId không hợp lệ.");
            int qid = ((Number) idValue).intValue();
            if (!uniqueQuestionIds.add(qid)) throw new IllegalArgumentException("Bài nộp chứa câu hỏi trùng lặp.");
            if (!quizDAO.questionBelongsToTopic(qid, ownedSession.getTopicId()))
                throw new IllegalArgumentException("Bài nộp chứa câu hỏi không thuộc chủ đề của phiên thi.");
        }

        // Lấy thông tin user để AI cá nhân hóa theo sở thích
        Optional<User> userOpt = userDAO.findById(userId);
        String userInterests = userOpt.map(User::getInterests).orElse(null);

        int correctCount = 0;
        int totalQuestions = submittedAnswers.size();
        List<Map<String, Object>> gradedAnswers = new ArrayList<>();
        List<RemedialLesson> remedialLessons = new ArrayList<>();
        List<CompletableFuture<RemedialLesson>> lessonFutures = new ArrayList<>();

        for (Map<String, Object> ansMap : submittedAnswers) {
            int questionId = ((Number) ansMap.get("questionId")).intValue();
            String chosenAnswer = (String) ansMap.get("userAnswer");
            String confidence = (String) ansMap.getOrDefault("confidenceLevel", "CERTAIN");

            Optional<Question> qOpt = questionDAO.findById(questionId);
            if (qOpt.isEmpty()) {
                continue;
            }

            Question q = qOpt.get();
            boolean isCorrect = chosenAnswer != null && chosenAnswer.trim().equalsIgnoreCase(q.getCorrectAnswer());
            if (isCorrect) {
                correctCount++;
            }

            // Lưu câu trả lời của user
            UserAnswer ua = new UserAnswer();
            ua.setSessionId(sessionId);
            ua.setQuestionId(questionId);
            ua.setUserAnswer(chosenAnswer != null ? chosenAnswer.trim().toUpperCase() : "");
            ua.setCorrect(isCorrect);
            ua.setConfidenceLevel("GUESS".equalsIgnoreCase(confidence) ? "GUESS" : "CERTAIN");

            int answerId = quizDAO.saveAnswer(ua);
            ua.setAnswerId(answerId);

            // Ghi nhận phản hồi câu hỏi
            Map<String, Object> graded = new HashMap<>();
            graded.put("questionId", questionId);
            graded.put("questionText", q.getQuestionText());
            graded.put("chosenAnswer", chosenAnswer);
            graded.put("correctAnswer", q.getCorrectAnswer());
            graded.put("isCorrect", isCorrect);
            graded.put("confidenceLevel", ua.getConfidenceLevel());
            graded.put("explanation", q.getExplanation());
            gradedAnswers.add(graded);

            // ★ Confidence Tagging & Error Detection: Kích hoạt AI song song nếu SAI hoặc ĐÚNG nhưng GUESS
            if (answerId > 0 && ua.needsAIAnalysis()) {
                final Question finalQ = q;
                final UserAnswer finalUa = ua;
                final int finalAnswerId = answerId;
                lessonFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        RemedialLesson lesson = aiService.analyzeError(finalQ, finalUa, userInterests);
                        if (lesson == null) {
                            lesson = FallbackService.generateFallbackLesson(finalQ, finalUa);
                        }
                        lesson.setUserId(userId);
                        lesson.setAnswerId(finalAnswerId);
                        return lesson;
                    } catch (Exception e) {
                        RemedialLesson fb = FallbackService.generateFallbackLesson(finalQ, finalUa);
                        fb.setUserId(userId);
                        fb.setAnswerId(finalAnswerId);
                        return fb;
                    }
                }));
            }
        }

        // Chờ kết quả AI với timeout tối đa 8 giây (đảm bảo request không bao giờ bị Render / Client timeout)
        for (CompletableFuture<RemedialLesson> future : lessonFutures) {
            try {
                RemedialLesson lesson = future.get(8, TimeUnit.SECONDS);
                if (lesson != null) {
                    quizDAO.saveRemedialLesson(lesson);
                    remedialLessons.add(lesson);
                }
            } catch (Exception e) {
                future.cancel(true);
            }
        }

        // Chuẩn hóa điểm về thang 10 chuẩn (vd: 3.8/10 hoặc 8.5/10)
        double score10 = totalQuestions > 0 ? ((double) correctCount / totalQuestions) * 10.0 : 0.0;
        double roundedScore10 = Math.round(score10 * 10.0) / 10.0;
        double percentage = totalQuestions > 0 ? ((double) correctCount / totalQuestions) * 100.0 : 0.0;
        double roundedPercentage = Math.round(percentage * 10.0) / 10.0;

        quizDAO.completeSession(sessionId, correctCount, roundedScore10);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("userId", userId);
        response.put("totalQuestions", totalQuestions);
        response.put("correctCount", correctCount);
        response.put("score", roundedScore10);
        response.put("percentage", roundedPercentage);
        response.put("gradedAnswers", gradedAnswers);
        response.put("remedialLessons", remedialLessons);

        return response;
    }

    /**
     * Lấy lịch sử làm bài của học sinh.
     */
    public List<QuizSession> getUserHistory(int userId) {
        return quizDAO.getHistoryByUser(userId);
    }

    /**
     * Lấy chi tiết phiên làm bài kèm các bài học củng cố đã tạo.
     */
    public Map<String, Object> getSessionDetails(int sessionId, int userId) {
        QuizSession session = quizDAO.findSessionForUser(sessionId, userId);
        if (session == null) throw new SecurityException("Bạn không có quyền xem phiên làm bài này.");
        List<UserAnswer> answers = quizDAO.getAnswersBySession(sessionId);
        List<RemedialLesson> lessons = quizDAO.getRemedialLessonsBySession(sessionId);

        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", sessionId);
        details.put("session", session);
        details.put("answers", answers);
        details.put("remedialLessons", lessons);
        return details;
    }

    /**
     * Tái tạo kết quả chấm điểm cho phiên thi đã nộp trước đó (Idempotent recovery).
     */
    private Map<String, Object> buildExistingSubmissionResult(QuizSession session, int userId) {
        int sessionId = session.getSessionId();
        List<UserAnswer> userAnswers = quizDAO.getAnswersBySession(sessionId);
        List<RemedialLesson> lessons = quizDAO.getRemedialLessonsBySession(sessionId);

        List<Map<String, Object>> gradedAnswers = new ArrayList<>();
        int correctCount = 0;
        for (UserAnswer ua : userAnswers) {
            if (ua.isCorrect()) correctCount++;
            Optional<Question> qOpt = questionDAO.findById(ua.getQuestionId());
            Map<String, Object> graded = new HashMap<>();
            graded.put("questionId", ua.getQuestionId());
            graded.put("chosenAnswer", ua.getUserAnswer());
            graded.put("isCorrect", ua.isCorrect());
            graded.put("confidenceLevel", ua.getConfidenceLevel());
            if (qOpt.isPresent()) {
                Question q = qOpt.get();
                graded.put("questionText", q.getQuestionText());
                graded.put("correctAnswer", q.getCorrectAnswer());
                graded.put("explanation", q.getExplanation());
            }
            gradedAnswers.add(graded);
        }

        int total = session.getTotalQuestions() > 0 ? session.getTotalQuestions() : userAnswers.size();
        double score = session.getCompletedAt() != null ? session.getScore() : (total > 0 ? Math.round(((double) correctCount / total) * 10.0 * 10.0) / 10.0 : 0.0);
        double percentage = total > 0 ? Math.round(((double) correctCount / total) * 100.0 * 10.0) / 10.0 : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("userId", userId);
        response.put("totalQuestions", total);
        response.put("correctCount", correctCount);
        response.put("score", score);
        response.put("percentage", percentage);
        response.put("gradedAnswers", gradedAnswers);
        response.put("remedialLessons", lessons);
        return response;
    }
}
