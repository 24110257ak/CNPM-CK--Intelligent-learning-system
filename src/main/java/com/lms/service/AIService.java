package com.lms.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lms.model.Question;
import com.lms.model.RemedialLesson;
import com.lms.model.UserAnswer;
import com.lms.util.ConfigLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service tích hợp Google Gemini API chính thức (gemini-2.5-flash).
 * Đạt chuẩn ADR-005 (ResponseSchema structured JSON) và ADR-008 (AI Fallback Buffer).
 */
public class AIService {

    private static final String MODEL_NAME = getEffectiveModel();
    private final Client client;
    private final boolean isConfigured;

    private static String getEffectiveModel() {
        String model = ConfigLoader.get("GEMINI_MODEL", "gemini-3.6-flash").trim();
        if (model.equalsIgnoreCase("gemini-2.5-flash") || model.equalsIgnoreCase("gemini-1.5-flash") || model.equalsIgnoreCase("gemini-2.0-flash")) {
            return "gemini-3.6-flash";
        }
        return model.isEmpty() ? "gemini-3.6-flash" : model;
    }

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

            GenerateContentResponse response;
            try {
                response = client.models.generateContent(MODEL_NAME, prompt, config);
            } catch (Exception modelErr) {
                String fallbackModel = MODEL_NAME.equals("gemini-3.6-flash") ? "gemini-3.8-flash" : "gemini-3.6-flash";
                response = client.models.generateContent(fallbackModel, prompt, config);
            }

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
                 + "Bạn có thể vào trang Google AI Studio (aistudio.google.com) tạo API Key miễn phí và cấu hình vào hệ thống để trò chuyện trực tiếp cùng Trợ Giảng AI nhé!";
        }

        try {
            String systemInstruction = PromptBuilder.getChatbotSystemInstruction(persona);
            String fullPrompt = (context != null && !context.isBlank() ? "Ngữ cảnh bài học: " + context + "\n\n" : "")
                              + "Câu hỏi của sinh viên: " + userMessage;

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .temperature(0.7f)
                    .build();

            GenerateContentResponse response;
            try {
                response = client.models.generateContent(MODEL_NAME, fullPrompt, config);
            } catch (Exception modelErr) {
                String fallbackModel = MODEL_NAME.equals("gemini-3.6-flash") ? "gemini-3.8-flash" : "gemini-3.6-flash";
                response = client.models.generateContent(fallbackModel, fullPrompt, config);
            }

            return response.text();
        } catch (Exception e) {
            System.err.println("[AIService] ❌ Gọi Gemini Chat thất bại: " + e.getMessage());
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            if (errorMsg.contains("403") || errorMsg.contains("permission_denied") || errorMsg.contains("denied access")) {
                return "⚠️ **Lỗi xác thực API Key (403 Permission Denied)**:\n"
                     + "Google thông báo dự án của API Key này đã bị từ chối truy cập (*Your project has been denied access*).\n\n"
                     + "👉 **Nguyên nhân & Cách khắc phục:**\n"
                     + "- Key hiện tại không có quyền gọi Generative Language API hoặc dự án Google Cloud đã bị khóa/vô hiệu hóa.\n"
                     + "- Bạn hãy truy cập [Google AI Studio (aistudio.google.com)](https://aistudio.google.com), dùng một tài khoản Gmail cá nhân khác và bấm **Create API key** $\\rightarrow$ **Create in new project**.\n"
                     + "- Sau đó vào Render $\\rightarrow$ **Environment** $\\rightarrow$ Cập nhật lại biến `GEMINI_API_KEY` nhé!";
            } else if (errorMsg.contains("404") || errorMsg.contains("not found")) {
                return "⚠️ **Lỗi Model không tồn tại (404 Not Found)**:\n"
                     + "Model `" + MODEL_NAME + "` không khả dụng trên tài khoản Google của bạn. Vui lòng kiểm tra lại biến `GEMINI_MODEL` (khuyên dùng `gemini-2.5-flash`) trên Render!";
            } else if (errorMsg.contains("429") || errorMsg.contains("resource_exhausted") || errorMsg.contains("quota")) {
                return "⏳ **Hết hạn mức yêu cầu (429 Quota Exceeded)**:\n"
                     + "API Key đã vượt quá số lượt gọi miễn phí trong phút của Google. Bạn vui lòng chờ 1-2 phút rồi thử lại nhé!";
            }

            // Fallback trả lời hướng dẫn sử dụng nếu hỏi về hệ thống
            if (userMessage != null && (userMessage.toLowerCase().contains("cách sử dụng") || userMessage.toLowerCase().contains("hướng dẫn") || userMessage.toLowerCase().contains("help"))) {
                return "📖 **Hướng dẫn sử dụng Hệ Thống Học Tập Thông Minh (LMS AI):**\n"
                     + "1. **Làm bài thi trắc nghiệm:** Vào trang chủ, chọn chủ đề (OOP Java, Cấu trúc dữ liệu,...).\n"
                     + "2. **Confidence Tagging (Đoán mò vs Chắc chắn):** Khi chọn đáp án, hãy đánh dấu mức độ tự tin. Nếu đoán mò đúng, AI vẫn sẽ củng cố kiến thức cho bạn!\n"
                     + "3. **Chẩn đoán sai sót & Adaptive Remediation:** Sau khi nộp bài, hệ thống sẽ chẩn đoán lỗ hổng tư duy và cung cấp Mini-Quiz 3 câu để bạn ôn luyện phục hồi ngay lập tức.\n"
                     + "4. **Trợ giảng AI:** Luôn sẵn sàng giải đáp thắc mắc lý thuyết và bài tập 24/7 (khi đã cấu hình API Key hợp lệ).";
            }

            return "Hệ thống AI đang bận hoặc gặp sự cố kết nối dịch vụ. Bạn vui lòng kiểm tra lại GEMINI_API_KEY hoặc thử lại sau giây lát nhé!";
        }
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    /**
     * Dành cho Giảng viên / Admin: Tự động sinh danh sách câu hỏi trắc nghiệm chất lượng cao.
     */
    public List<Map<String, Object>> generateQuestionsForTeacher(String topicName, String difficulty, String misconceptionTag, int count, String promptHint) {
        List<Map<String, Object>> questionsList = new ArrayList<>();
        if (!isConfigured) {
            System.err.println("[AIService] ⚠️ Chưa cấu hình GEMINI_API_KEY để sinh câu hỏi.");
            return questionsList;
        }

        try {
            String prompt = PromptBuilder.buildTeacherQuestionGenPrompt(topicName, difficulty, misconceptionTag, count, promptHint);
            String systemInstruction = PromptBuilder.getTeacherCoPilotInstruction();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .responseMimeType("application/json")
                    .temperature(0.5f)
                    .build();

            GenerateContentResponse response;
            try {
                response = client.models.generateContent(MODEL_NAME, prompt, config);
            } catch (Exception modelErr) {
                String fallbackModel = MODEL_NAME.equals("gemini-3.6-flash") ? "gemini-3.8-flash" : "gemini-3.6-flash";
                response = client.models.generateContent(fallbackModel, prompt, config);
            }

            String jsonText = response.text();
            if (jsonText != null && !jsonText.isBlank()) {
                JsonArray arr = JsonParser.parseString(jsonText).getAsJsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        Map<String, Object> map = new HashMap<>();
                        map.put("questionText", obj.has("question_text") ? obj.get("question_text").getAsString() : "");
                        map.put("optionA", obj.has("option_a") ? obj.get("option_a").getAsString() : "");
                        map.put("optionB", obj.has("option_b") ? obj.get("option_b").getAsString() : "");
                        map.put("optionC", obj.has("option_c") ? obj.get("option_c").getAsString() : "");
                        map.put("optionD", obj.has("option_d") ? obj.get("option_d").getAsString() : "");
                        map.put("correctAnswer", obj.has("correct_answer") ? obj.get("correct_answer").getAsString().toUpperCase() : "A");
                        map.put("explanation", obj.has("explanation") ? obj.get("explanation").getAsString() : "");
                        map.put("difficulty", obj.has("difficulty") ? obj.get("difficulty").getAsString().toLowerCase() : (difficulty != null ? difficulty : "medium"));
                        map.put("misconceptionTag", obj.has("misconception_tag") ? obj.get("misconception_tag").getAsString().toLowerCase() : "mental_model_gap");
                        questionsList.add(map);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AIService] ❌ Gọi Gemini sinh câu hỏi thất bại: " + e.getMessage());
            e.printStackTrace();
        }

        return questionsList;
    }

    /**
     * Dành cho Giảng viên / Admin: Chatbot tư vấn sư phạm và thiết kế bài thi.
     */
    public String teacherChat(String userMessage, String context) {
        if (!isConfigured) {
            return "Xin chào Thầy/Cô! Hiện tại hệ thống đang ở chế độ ngoại tuyến do chưa cấu hình GEMINI_API_KEY hợp lệ. "
                 + "Vui lòng cấu hình API Key để kích hoạt Trợ Lý AI Co-Pilot hỗ trợ soạn đề.";
        }

        try {
            String systemInstruction = PromptBuilder.getTeacherCoPilotInstruction();
            String fullPrompt = (context != null && !context.isBlank() ? "Ngữ cảnh / Dữ liệu liên quan:\n" + context + "\n\n" : "")
                              + "Yêu cầu của Giảng viên: " + userMessage;

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .temperature(0.7f)
                    .build();

            GenerateContentResponse response;
            try {
                response = client.models.generateContent(MODEL_NAME, fullPrompt, config);
            } catch (Exception modelErr) {
                String fallbackModel = MODEL_NAME.equals("gemini-3.6-flash") ? "gemini-3.8-flash" : "gemini-3.6-flash";
                response = client.models.generateContent(fallbackModel, fullPrompt, config);
            }

            return response.text();
        } catch (Exception e) {
            System.err.println("[AIService] ❌ Gọi Teacher Chat thất bại: " + e.getMessage());
            return "Trợ lý AI tạm thời gặp sự cố kết nối (" + e.getMessage() + "). Thầy/Cô vui lòng thử lại sau giây lát nhé!";
        }
    }
}
