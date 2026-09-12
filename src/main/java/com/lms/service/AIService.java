package com.lms.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lms.model.Question;
import com.lms.model.RemedialLesson;
import com.lms.model.UserAnswer;
import com.lms.util.ConfigLoader;

/**
 * Service tích hợp Google Gemini API chính thức (gemini-2.5-flash).
 * Đạt chuẩn ADR-005 (ResponseSchema structured JSON) và ADR-008 (AI Fallback Buffer).
 */
public class AIService {

    private static final String MODEL_NAME = ConfigLoader.get("GEMINI_MODEL", "gemini-2.5-flash");
    private final Client client;
    private final boolean isConfigured;

    public AIService() {
        String apiKey = ConfigLoader.get("GEMINI_API_KEY", "").trim();
        if (isValidApiKey(apiKey)) {
            Client c = null;
            try {
                c = Client.builder().apiKey(apiKey).build();
            } catch (Exception e) {
                System.err.println("[AIService] ⚠️ Không thể khởi tạo Gemini Client: " + e.getMessage());
            }
            this.client = c;
            this.isConfigured = (this.client != null);
            if (this.isConfigured) {
                System.out.println("[AIService] 🚀 Google Gemini API đã sẵn sàng với model: " + MODEL_NAME);
            }
        } else {
            this.client = null;
            this.isConfigured = false;
            System.out.println("[AIService] ℹ️ GEMINI_API_KEY chưa hợp lệ hoặc đang để trống. Hệ thống tự động kích hoạt Fallback Buffer (ADR-008).");
        }
    }

    /**
     * Kiểm tra tính hợp lệ của API Key để tránh lỗi OkHttp header hoặc dùng nhầm text placeholder.
     */
    public static boolean isValidApiKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        // Tránh lỗi OkHttp "Unexpected char at in x-goog-api-key value" khi có ký tự có dấu/tiếng Việt
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) > 127) {
                return false;
            }
        }
        // Kiểm tra placeholder thông dụng
        String lower = key.toLowerCase();
        if (lower.contains("your_") || lower.contains("placeholder") || lower.contains("dán_")
                || lower.contains("dan_") || lower.contains("api_key") || lower.contains("here")) {
            return false;
        }
        // API Key chuẩn của Google AI Studio thường có ít nhất 20 ký tự (AIzaSy...)
        return key.length() >= 20;
    }

    /**
     * Phân tích câu hỏi bị sai hoặc câu hỏi đoán mò ('GUESS') và sinh bài học củng cố.
     */
    public RemedialLesson analyzeError(Question question, UserAnswer userAnswer, String userInterests) {
        if (!isConfigured) {
            return FallbackService.generateFallbackLesson(question, userAnswer);
        }

        try {
            String prompt = PromptBuilder.buildErrorAnalysisPrompt(question, userAnswer, userInterests);
            String systemInstruction = PromptBuilder.getPedagogicalSystemInstruction();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .responseMimeType("application/json")
                    .temperature(0.4f)
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    MODEL_NAME,
                    prompt,
                    config
            );

            String jsonText = response.text();
            if (jsonText != null && !jsonText.isBlank()) {
                JsonObject obj = JsonParser.parseString(jsonText).getAsJsonObject();
                RemedialLesson lesson = new RemedialLesson();
                lesson.setAnswerId(userAnswer.getAnswerId());
                lesson.setErrorReason(obj.has("error_reason") ? obj.get("error_reason").getAsString() : "Lỗi phân tích cú pháp hoặc quan niệm");
                lesson.setLessonContent(obj.has("lesson_content") ? obj.get("lesson_content").getAsString() : question.getExplanation());
                lesson.setPracticeQuestion(obj.has("practice_question") ? obj.get("practice_question").getAsString() : "");
                lesson.setMisconceptionType(obj.has("misconception_type") ? obj.get("misconception_type").getAsString() : "mental_model_gap");
                return lesson;
            }
        } catch (Exception e) {
            System.err.println("[AIService] ❌ Gọi Gemini API phân tích lỗi thất bại: " + e.getMessage());
            System.out.println("[AIService] 🔄 Kích hoạt FallbackService (ADR-008)...");
        }

        return FallbackService.generateFallbackLesson(question, userAnswer);
    }

    /**
     * Chatbot trợ giảng giải đáp thắc mắc của sinh viên.
     */
    public String chat(String userMessage, String persona, String context) {
        if (!isConfigured) {
            return "Xin chào! Hiện tại hệ thống đang chạy ở chế độ ngoại tuyến (Offline Fallback — chưa cấu hình GEMINI_API_KEY hợp lệ). "
                 + "Bạn vui lòng lấy API Key miễn phí từ Google AI Studio (bắt đầu bằng 'AIzaSy...') và cấu hình vào hệ thống để trò chuyện trực tiếp cùng Trợ Giảng AI nhé!";
        }

        try {
            String systemInstruction = PromptBuilder.getChatbotSystemInstruction(persona);
            String fullPrompt = (context != null && !context.isBlank() ? "Ngữ cảnh bài học: " + context + "\n\n" : "")
                              + "Câu hỏi của sinh viên: " + userMessage;

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .temperature(0.7f)
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    MODEL_NAME,
                    fullPrompt,
                    config
            );

            return response.text();
        } catch (Exception e) {
            System.err.println("[AIService] ❌ Gọi Gemini Chat thất bại: " + e.getMessage());
            return "Hệ thống AI đang bận hoặc gặp sự cố kết nối dịch vụ. Bạn vui lòng kiểm tra lại GEMINI_API_KEY hoặc thử lại sau giây lát nhé!";
        }
    }

    public boolean isConfigured() {
        return isConfigured;
    }
}
