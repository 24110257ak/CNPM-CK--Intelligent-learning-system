package com.lms.service;

import com.lms.model.Question;
import com.lms.model.UserAnswer;

/**
 * Xây dựng các Prompt chuyên nghiệp gửi tới Google Gemini API.
 * Hỗ trợ Confidence Tagging (ADR-009) và cá nhân hóa theo sở thích học sinh.
 */
public class PromptBuilder {

    /**
     * System instruction cho AI Giáo viên / Chuyên gia phân tích sư phạm.
     */
    public static String getPedagogicalSystemInstruction() {
        return """
            Bạn là một Giảng viên Công nghệ Thông tin kiêm Chuyên gia Sư phạm AI hàng đầu.
            Nhiệm vụ của bạn là chẩn đoán lỗ hổng kiến thức (knowledge gap) và quan niệm sai lầm (misconception) của sinh viên khi làm bài kiểm tra.
            
            Nguyên tắc phản hồi:
            1. KHÔNG chỉ trích, luôn dùng ngôn từ động viên, khuyến khích (growth mindset).
            2. Đi thẳng vào bản chất: Giải thích tại sao sinh viên lại chọn nhầm phương án đó (tâm lý bẫy trắc nghiệm, hiểu nhầm khái niệm gì).
            3. Nếu sinh viên chọn ĐÚNG nhưng gắn nhãn 'GUESS' (đoán mò/không chắc chắn): hãy củng cố lại lý do vì sao đáp án đó là đúng để biến kiến thức may rủi thành kiến thức vững chắc.
            4. Viết bài học củng cố ngắn gọn (khoảng 3-4 đoạn ngắn), có ví dụ code minh họa rõ ràng và ẩn dụ dễ nhớ.
            5. Đưa ra đúng 1 câu hỏi thực hành tương đương (có 4 lựa chọn A, B, C, D và đáp án đúng) để sinh viên làm lại ngay.
            6. Phân loại misconception_type vào một trong các nhóm:
               - syntax_swap (nhầm lẫn cú pháp)
               - boundary_blindness (quên điều kiện biên, out-of-bounds, null)
               - mental_model_gap (lỗ hổng mô hình tư duy, OOP/bộ nhớ)
               - logic_flaw (sai sót luồng logic)
            """;
    }

    /**
     * Tạo User Prompt phân tích một câu hỏi cần củng cố.
     */
    public static String buildErrorAnalysisPrompt(Question question, UserAnswer userAnswer, String userInterests) {
        StringBuilder sb = new StringBuilder();
        sb.append("Phân tích câu hỏi sau cho sinh viên:\n\n");
        sb.append("Đề bài: ").append(question.getQuestionText()).append("\n");
        sb.append("A: ").append(question.getOptionA()).append("\n");
        sb.append("B: ").append(question.getOptionB()).append("\n");
        sb.append("C: ").append(question.getOptionC()).append("\n");
        sb.append("D: ").append(question.getOptionD()).append("\n");
        sb.append("Đáp án chính xác: ").append(question.getCorrectAnswer()).append("\n");
        if (question.getExplanation() != null && !question.getExplanation().isBlank()) {
            sb.append("Giải thích chuẩn: ").append(question.getExplanation()).append("\n");
        }
        sb.append("\n--- Thông tin lựa chọn của sinh viên ---\n");
        sb.append("Sinh viên chọn: ").append(userAnswer.getUserAnswer()).append("\n");
        sb.append("Kết quả: ").append(userAnswer.isCorrect() ? "ĐÚNG" : "SAI").append("\n");
        sb.append("Mức độ tự tin (Confidence Tag): ").append(userAnswer.getConfidenceLevel()).append("\n");

        if ("GUESS".equalsIgnoreCase(userAnswer.getConfidenceLevel())) {
            sb.append("GHI CHÚ: Sinh viên tự đánh giá là ĐOÁN MÒ (GUESS). Vui lòng củng cố chắc chắn lại cơ sở lý thuyết để loại bỏ sự phân vân.\n");
        }

        if (userInterests != null && !userInterests.isBlank()) {
            sb.append("Sở thích của sinh viên: ").append(userInterests)
              .append(" (Nếu có thể, hãy dùng ẩn dụ liên quan đến sở thích này để giải thích khái niệm).\n");
        }

        sb.append("""
            
            Hãy trả về một JSON object đúng cấu trúc sau (không kèm markdown format ngoài json):
            {
              "error_reason": "string (giải thích tại sao sinh viên nhầm lẫn hoặc tại sao phân vân)",
              "lesson_content": "string (nội dung bài học củng cố, có ví dụ code ngắn)",
              "practice_question": "string (1 câu trắc nghiệm thực hành kèm 4 lựa chọn và đáp án)",
              "misconception_type": "string (syntax_swap | boundary_blindness | mental_model_gap | logic_flaw)"
            }
            """);

        return sb.toString();
    }

    /**
     * System instruction cho AI Chatbot trợ giảng.
     */
    public static String getChatbotSystemInstruction(String persona) {
        String personaDesc = switch (persona != null ? persona.toLowerCase() : "senior_dev") {
            case "professor" -> "Giáo sư Đại học uyên bác, giải thích cặn kẽ bản chất từ gốc rễ lý thuyết, khuyến khích tư duy phản biện.";
            case "peer_tutor" -> "Bạn học kèm nhiệt tình, xưng hô 'mình - bạn', giải thích bằng ngôn ngữ gần gũi, mẹo nhớ nhanh.";
            default -> "Senior Software Engineer thực tế, tập trung vào best practices, kinh nghiệm thực chiến trong doanh nghiệp.";
        };

        return """
            Bạn là Trợ giảng AI thông minh của Hệ thống học tập LMS.
            Hình tượng (Persona) của bạn là: %s
            
            Nguyên tắc giao tiếp:
            - Trả lời bằng tiếng Việt thân thiện, rõ ràng, định dạng Markdown đẹp mắt.
            - Hỗ trợ giải thích code, thuật toán, kiến trúc hệ thống và hướng dẫn sửa lỗi bài tập.
            - Nếu sinh viên hỏi ngoài lề lập trình/khoa học máy tính, lịch sự hướng dẫn quay lại chủ đề học tập.
            """.formatted(personaDesc);
    }

    /**
     * System instruction chuyên biệt dành riêng cho Giảng viên / Quản trị viên (Teacher AI Co-Pilot).
     */
    public static String getTeacherCoPilotInstruction() {
        return """
            Bạn là Trợ Lý Sư Phạm & Chuyên Gia Thiết Kế Đề Thi CNTT Cấp Cao dành cho Giảng Viên.
            Vai trò của bạn:
            1. Hỗ trợ giảng viên soạn thảo câu hỏi trắc nghiệm, bài tập lập trình có tính phân hóa cao.
            2. Thiết kế các phương án bẫy nhiễu (distractors) tinh tế dựa trên quan niệm sai lầm phổ biến của sinh viên (syntax_swap, boundary_blindness, mental_model_gap, logic_flaw).
            3. Phân tích độ khó, kiểm định tính tường minh, tránh câu hỏi mơ hồ (ambiguous).
            4. Phản hồi bằng tiếng Việt chuẩn mực học thuật, súc tích, định dạng Markdown rõ ràng.
            """;
    }

    /**
     * Prompt yêu cầu AI sinh danh sách câu hỏi trắc nghiệm chuẩn theo yêu cầu của Giảng viên/Người đóng góp.
     * Hỗ trợ mọi môn học, chủ đề tùy ý hoặc đề thi hỗn hợp đa lĩnh vực.
     */
    public static String buildTeacherQuestionGenPrompt(String topicName, String difficulty, String misconceptionTag, int count, String promptHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hãy tạo chính xác ").append(count).append(" câu hỏi trắc nghiệm học tập chất lượng cao dành cho kỳ thi/bài kiểm tra/diễn đàn học tập.\n\n");
        sb.append("--- Yêu cầu thông số ---\n");
        sb.append("- Chủ đề / Môn học (hoặc đề hỗn hợp): ").append(topicName != null && !topicName.isBlank() ? topicName : "Kiến thức tổng hợp").append("\n");
        sb.append("- Mức độ khó: ").append(difficulty != null && !difficulty.isBlank() ? difficulty : "medium").append(" (easy / medium / hard)\n");
        if (misconceptionTag != null && !misconceptionTag.isBlank() && !misconceptionTag.equalsIgnoreCase("all")) {
            sb.append("- Nhóm bẫy nhận thức mục tiêu cần kiểm tra: ").append(misconceptionTag).append(" (syntax_swap / boundary_blindness / mental_model_gap / logic_flaw / other)\n");
        }
        if (promptHint != null && !promptHint.isBlank()) {
            sb.append("- Yêu cầu bổ sung hoặc chủ đề kết hợp: ").append(promptHint).append("\n");
        }

        sb.append("""

            --- Nguyên tắc thiết kế câu hỏi ---
            1. Đề bài (question_text) cần rõ ràng, súc tích, thực tế. Nếu là câu hỏi kỹ thuật/lập trình, hãy đặt code trong khối Markdown thích hợp (vd: ```java, ```python, ```sql...). Nếu là toán học/kinh tế/khoa học, hãy trình bày công thức và dữ kiện rõ ràng.
            2. Có đủ 4 phương án A, B, C, D phân hóa rõ rệt, tính hợp lý cao, không đặt phương án vô lý hoặc quá lộ liễu.
            3. Đáp án đúng (correct_answer) là một trong các chữ cái: 'A', 'B', 'C', hoặc 'D'.
            4. Lời giải thích (explanation) phải chi tiết, chuẩn mực sư phạm: vì sao đáp án đó là đúng, và các phương án sai đã đánh trúng bẫy tư duy nào.
            5. Gắn nhãn misconception_tag là bẫy nhận thức cụ thể của câu hỏi (có thể là một trong các nhóm kinh điển syntax_swap, boundary_blindness, mental_model_gap, logic_flaw, hoặc bất kỳ bẫy tư duy đặc thù nào phù hợp với môn học này dưới dạng chuỗi ngắn gọn).

            Hãy trả về một JSON Array chứa danh sách các câu hỏi, đúng cấu trúc JSON sau (không kèm text nào ngoài JSON):
            [
              {
                "question_text": "string (nội dung câu hỏi, có thể chứa markdown)",
                "option_a": "string",
                "option_b": "string",
                "option_c": "string",
                "option_d": "string",
                "correct_answer": "A hoặc B hoặc C hoặc D",
                "explanation": "string (giải thích chi tiết sư phạm)",
                "difficulty": "easy hoặc medium hoặc hard",
                "misconception_tag": "string (tên bẫy tư duy)"
              }
            ]
            """);

        return sb.toString();
    }

    /**
     * Prompt yêu cầu AI gợi ý các bẫy tư duy (misconceptions) đặc thù theo môn học / chủ đề bất kỳ.
     */
    public static String buildSuggestMisconceptionsPrompt(String topicName) {
        return """
            Bạn là Chuyên Gia Sư Phạm & Thiết Kế Đề Thi.
            Chủ đề / Môn học được giảng viên nhập là: "%s"

            Hãy phân tích và gợi ý từ 4 đến 6 quan niệm sai lầm phổ biến nhất (Cognitive Traps / Misconceptions) mà người học hay mắc phải ở môn học/chủ đề này.
            Các bẫy này cần thực tế, có tính phân hóa cao và thích hợp để dùng làm phương án nhiễu (distractor) trong câu hỏi trắc nghiệm.

            Hãy trả về một JSON Array duy nhất (không có bất kỳ văn bản nào ngoài JSON), định dạng:
            [
              {
                "tag": "snake_case_tag_ngắn_gọn",
                "label": "Tên bẫy ngắn gọn súc tích",
                "description": "Mô tả sinh viên thường hiểu lầm hay tính toán sai ở điểm nào"
              }
            ]
            """.formatted(topicName != null && !topicName.isBlank() ? topicName : "Kiến thức tổng hợp");
    }

    /**
     * Prompt thẩm định (AI Validation) tính sư phạm và phạm vi của môn học / chủ đề.
     */
    public static String buildValidateTopicPrompt(String topicName) {
        return """
            Bạn là Chuyên Gia Thẩm Định Chương Trình Đào Tạo.
            Giảng viên vừa nhập tên chủ đề / môn học: "%s"

            Hãy thẩm định xem tên chủ đề này có hợp lệ, rõ ràng và phù hợp để tạo ngân hàng câu hỏi hay không.
            Trả về đúng định dạng JSON sau (không có văn bản ngoài JSON):
            {
              "isValid": true,
              "field": "Tên lĩnh vực học thuật (vd: Công Nghệ Thông Tin, Kinh Tế Học, Khoa Học Tự Nhiên, v.v.)",
              "clarity": "high / medium / low",
              "feedback": "Nhận xét ngắn gọn 1-2 câu về tính phù hợp, tính bao quát hoặc khuyến nghị sư phạm",
              "suggestedSubtopics": ["Chủ đề phụ gợi ý 1", "Chủ đề phụ gợi ý 2", "Chủ đề phụ gợi ý 3"]
            }
            """.formatted(topicName != null && !topicName.isBlank() ? topicName : "");
    }

    /**
     * Prompt thẩm định (AI Validation) một bẫy tư duy đối với chủ đề đã chọn.
     */
    public static String buildValidateMisconceptionPrompt(String topicName, String misconception) {
        return """
            Bạn là Chuyên Gia Sư Phạm.
            Môn học / Chủ đề: "%s"
            Bẫy tư duy mà giảng viên muốn kiểm tra: "%s"

            Hãy thẩm định tính phù hợp và khả thi của bẫy tư duy này trong việc thiết kế câu hỏi trắc nghiệm.
            Trả về JSON duy nhất (không có văn bản ngoài JSON):
            {
              "isValid": true,
              "feedback": "Nhận xét chuyên môn về bẫy tư duy này",
              "distractorTip": "Gợi ý cách thiết kế phương án nhiễu để bẫy người học hiệu quả nhất"
            }
            """.formatted(topicName != null ? topicName : "Tổng hợp", misconception != null ? misconception : "");
    }

    /**
     * Prompt hỗ trợ Giảng viên hoàn thiện câu hỏi (AI Assist Question Drafting).
     */
    public static String buildAssistQuestionPrompt(String topicName, String questionPrompt, String difficulty) {
        return """
            Bạn là Trợ Lý Sư Phạm Soạn Câu Hỏi Thi.
            Chủ đề: "%s"
            Mức độ: "%s"
            Ý tưởng hoặc nội dung câu hỏi giảng viên vừa nhập:
            "%s"

            Dựa trên nội dung trên, hãy hoàn thiện thành một câu hỏi trắc nghiệm hoàn chỉnh, gồm:
            - question_text: Câu hỏi được chau chuốt sư phạm (nếu có code hãy định dạng markdown thích hợp).
            - option_a, option_b, option_c, option_d: 4 phương án rõ ràng, có phân hóa và chứa bẫy tư duy tinh tế.
            - correct_answer: Một trong 4 chữ cái 'A', 'B', 'C', hoặc 'D'.
            - explanation: Lời giải thích cặn kẽ chuẩn sư phạm tại sao đúng và tại sao các phương án khác sai.
            - misconception_tag: Bẫy tư duy trọng tâm ngắn gọn.

            Trả về JSON duy nhất (không có văn bản ngoài JSON):
            {
              "question_text": "...",
              "option_a": "...",
              "option_b": "...",
              "option_c": "...",
              "option_d": "...",
              "correct_answer": "A",
              "explanation": "...",
              "misconception_tag": "..."
            }
            """.formatted(
                topicName != null && !topicName.isBlank() ? topicName : "Kiến thức chung",
                difficulty != null ? difficulty : "medium",
                questionPrompt != null ? questionPrompt : ""
            );
    }
}
