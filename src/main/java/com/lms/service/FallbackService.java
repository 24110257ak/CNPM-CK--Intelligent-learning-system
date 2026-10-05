package com.lms.service;

import com.lms.model.Question;
import com.lms.model.RemedialLesson;
import com.lms.model.UserAnswer;

/**
 * Service dự phòng (ADR-008 AI Fallback Buffer).
 * Đảm bảo hệ thống vẫn hoạt động liền mạch ngay cả khi mất mạng,
 * hết quota Gemini API hoặc chưa cấu hình API Key.
 */
public class FallbackService {

    /**
     * Tạo bài học củng cố nội bộ từ dữ liệu câu hỏi đã có trong CSDL.
     */
    public static RemedialLesson generateFallbackLesson(Question question, UserAnswer userAnswer) {
        RemedialLesson lesson = new RemedialLesson();
        lesson.setAnswerId(userAnswer.getAnswerId());

        boolean isGuess = "GUESS".equalsIgnoreCase(userAnswer.getConfidenceLevel());
        String selected = userAnswer.getUserAnswer();
        String correct = question.getCorrectAnswer();
        String explanation = question.getExplanation() != null && !question.getExplanation().isBlank()
                ? question.getExplanation()
                : "Vui lòng xem lại lý thuyết cốt lõi của bài học này.";

        if (userAnswer.isCorrect() && isGuess) {
            lesson.setErrorReason("Bạn đã chọn đúng đáp án (" + correct + ") nhưng với mức độ tự tin là 'ĐOÁN MÒ'.");
            lesson.setLessonContent(
                    "### 💡 Củng Cố Kiến Thức Phân Vân\n\n"
                    + "Bạn đã có trực giác tốt khi chọn đáp án **" + correct + "**! "
                    + "Tuy nhiên, để tự tin 100% trong kỳ thi thực tế, hãy nắm vững nguyên lý sau:\n\n"
                    + "> " + explanation + "\n\n"
                    + "Hãy ghi nhớ các từ khóa quan trọng và kiểm tra lại câu hỏi tương tự."
            );
            lesson.setPracticeQuestion(
                    "Theo bạn, điều kiện tiên quyết nào làm cho phương án " + correct + " luôn đúng trong trường hợp này?\n"
                    + "A. Dựa vào cú pháp đặc tả ngôn ngữ\n"
                    + "B. Do quy tắc quản lý bộ nhớ\n"
                    + "C. Cả A và B đều đúng (Đáp án gợi ý: C)\n"
                    + "D. Không có quy tắc nào"
            );
            lesson.setMisconceptionType("mental_model_gap");
        } else {
            lesson.setErrorReason("Bạn đã chọn phương án (" + selected + ") thay vì đáp án đúng (" + correct + ").");
            lesson.setLessonContent(
                    "### 📖 Phân Tích & Hướng Dẫn Ôn Tập\n\n"
                    + "Khi gặp câu hỏi này, việc chọn nhầm phương án **" + selected + "** thường do chưa phân biệt rõ các khái niệm liên quan.\n\n"
                    + "**Giải thích chuẩn từ giảng viên:**\n"
                    + "> " + explanation + "\n\n"
                    + "**Lời khuyên học tập:**\n"
                    + "- Đọc kỹ yêu cầu đề bài trước khi chọn phương án.\n"
                    + "- Dùng phương pháp loại trừ các đáp án vi phạm nguyên tắc cơ bản."
            );
            lesson.setPracticeQuestion(
                    "Hãy tự trả lời lại: Tại sao phương án " + correct + " lại chính xác hơn phương án " + selected + " theo nguyên lý trên?"
            );
            lesson.setMisconceptionType("mental_model_gap");
        }

        return lesson;
    }

    /**
     * Bộ máy Sư phạm Thông minh (ADR-008 AI Fallback Engine):
     * Tự động sinh ngân hàng câu hỏi trắc nghiệm chất lượng cao, chuẩn hóa Bloom
     * và bẫy tư duy khi Gemini API offline, hết quota hoặc chưa cấu hình API Key.
     */
    public static java.util.List<java.util.Map<String, Object>> generateFallbackQuestions(
            String topicName, String difficulty, String misconceptionTag, int count, String promptHint) {

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        String topicLower = (topicName != null ? topicName.toLowerCase() : "") + " " + (promptHint != null ? promptHint.toLowerCase() : "");
        String diff = (difficulty != null && !difficulty.isBlank() && !difficulty.equalsIgnoreCase("all")) ? difficulty.toLowerCase() : "medium";

        // 1. Chuyên đề: Xử lý tiếng nói & Deep Learning (BiGRU, GRU, LSTM, RNN, Audio)
        if (topicLower.contains("tiếng nói") || topicLower.contains("tieng noi") || topicLower.contains("speech")
                || topicLower.contains("bigru") || topicLower.contains("gru") || topicLower.contains("lstm")
                || topicLower.contains("âm thanh") || topicLower.contains("audio") || topicLower.contains("rnn")) {

            result.add(createQuestionMap(
                    "Trong bài toán xử lý chuỗi tín hiệu tiếng nói, ưu điểm cốt lõi của mạng BiGRU (Bidirectional Gated Recurrent Unit) so với mạng GRU đơn hướng là gì?",
                    "BiGRU tổng hợp đồng thời cả ngữ cảnh quá khứ (forward) và ngữ cảnh tương lai (backward) tại mỗi khung thời gian t.",
                    "BiGRU loại bỏ hoàn toàn các cổng Reset gate và Update gate để giảm thời gian tính toán.",
                    "BiGRU chỉ cần huấn luyện trên một nửa dữ liệu so với GRU đơn hướng.",
                    "BiGRU thay thế hàm kích hoạt phi tuyến tính bằng hàm tuyến tính thuần túy.",
                    "A",
                    "BiGRU gồm hai luồng RNN riêng biệt: luồng Forward duyệt từ t=1 đến T (nắm bắt âm tố phía trước) và luồng Backward duyệt từ t=T về 1 (nắm bắt âm tố phía sau), giúp nhận diện chính xác các âm vần phụ thuộc ngữ cảnh.",
                    "medium",
                    "mental_model_gap"
            ));

            result.add(createQuestionMap(
                    "Trong kiến trúc tế bào (cell) của mạng GRU, hai cổng (gates) đóng vai trò kiểm soát dòng dữ liệu là:",
                    "Input gate và Forget gate.",
                    "Reset gate và Update gate.",
                    "Output gate và Memory gate.",
                    "Candidate hidden state và Output gate.",
                    "B",
                    "Khác với LSTM sử dụng 3 cổng (Forget, Input, Output), GRU tinh gọn kiến trúc thành 2 cổng: Reset gate (quyết định mức độ kết hợp thông tin quá khứ với đầu vào hiện tại) và Update gate (quyết định lượng thông tin quá khứ cần truyền tiếp).",
                    "easy",
                    "syntax_swap"
            ));

            result.add(createQuestionMap(
                    "Khi tiền xử lý tín hiệu âm thanh đưa vào BiGRU, nếu không áp dụng Masking cho các batch âm thanh có Zero-Padding ở đuôi thì mô hình dễ mắc bẫy nhận thức nào?",
                    "syntax_swap: Đặt sai tên biến batch_size trong hàm loss.",
                    "boundary_blindness: Luồng Backward sẽ đọc các giá trị đệm 0 như tín hiệu âm thanh hợp lệ, làm sai lệch trạng thái ẩn ban đầu.",
                    "logic_flaw: Tần số lấy mẫu (Sample Rate) tự động bị giảm một nửa.",
                    "mental_model_gap: BiGRU tự động chuyển hóa thành mạng nơ-ron tích chập (CNN).",
                    "B",
                    "Đây là bẫy Boundary Blindness điển hình. Luồng Backward bắt đầu duyệt từ cuối chuỗi âm thanh; nếu không dùng Masking, các giá trị 0 của padding sẽ làm biến dạng trạng thái ẩn trước khi chạm đến tín hiệu giọng nói thực tế.",
                    "hard",
                    "boundary_blindness"
            ));

            result.add(createQuestionMap(
                    "Trong tế bào GRU, cổng Update gate (z_t) đóng vai trò tương đương sự kết hợp của những cổng nào trong mạng LSTM?",
                    "Input gate và Forget gate.",
                    "Forget gate và Output gate.",
                    "Input gate và Output gate.",
                    "Cell state và Hidden state.",
                    "A",
                    "Cổng Update gate trong GRU điều khiển việc quên thông tin cũ qua hệ số (1 - z_t) và nạp ứng viên thông tin mới qua hệ số z_t, tương đương với sự kết hợp của Forget gate và Input gate trong LSTM.",
                    "medium",
                    "mental_model_gap"
            ));

            result.add(createQuestionMap(
                    "Tại sao trong bài toán Nhận dạng tiếng nói thời gian thực (Streaming ASR), mạng BiGRU tiêu chuẩn không thể triển khai trực tiếp mà cần dùng BiGRU phân đoạn (Chunk-based) hoặc GRU đơn hướng?",
                    "Vì BiGRU yêu cầu dung lượng bộ nhớ lớn hơn 100 lần so với mô hình Transformer.",
                    "Vì luồng Backward đòi hỏi phải có toàn bộ chuỗi âm thanh tương lai mới bắt đầu tính toán được, gây trễ vô hạn trong thời gian thực.",
                    "Vì hàm mất mát CTC (Connectionist Temporal Classification) không tương thích với mạng 2 chiều.",
                    "Vì BiGRU chỉ xử lý được chuỗi ký tự văn bản, không nhận ma trận đặc trưng Mel-Spectrogram.",
                    "B",
                    "Bẫy tư duy logic_flaw: Trong streaming trực tiếp, người dùng vừa nói thì hệ thống phải giải mã ngay. Luồng Backward cần toàn bộ câu nói mới chạy được nên không thể áp dụng BiGRU toàn cục cho thời gian thực.",
                    "hard",
                    "logic_flaw"
            ));

            result.add(createQuestionMap(
                    "Khi trích xuất đặc trưng âm thanh đầu vào cho mô hình BiGRU, dạng biểu diễn nào sau đây phổ biến và bảo toàn thông tin phổ tần số tốt nhất?",
                    "Mã nhị phân ASCII của tập tin ghi âm.",
                    "Ma trận Mel-Spectrogram hoặc hệ số MFCC (Mel-Frequency Cepstral Coefficients).",
                    "Chỉ số Decibel trung bình của toàn bộ tệp âm thanh.",
                    "Tần số lấy mẫu cố định 44.1 kHz dạng số nguyên đơn lẻ.",
                    "B",
                    "Mel-Spectrogram và MFCC ánh xạ miền thời gian sang miền tần số theo thang đo Mel mô phỏng độ nhạy thính giác người, là đầu vào chuẩn mực cho các mô hình BiGRU/RNN trong ASR.",
                    "easy",
                    "mental_model_gap"
            ));
        }

        // 2. Chuyên đề: Lập trình Java & Hướng Đối Tượng (OOP)
        else if (topicLower.contains("java") || topicLower.contains("oop") || topicLower.contains("hướng đối tượng")
                || topicLower.contains("huong doi tuong") || topicLower.contains("lập trình")) {

            result.add(createQuestionMap(
                    "Trong Java, sự khác biệt bản chất giữa Interface và Abstract Class khi thiết kế kiến trúc phần mềm là gì?",
                    "Abstract Class có thể chứa constructor và trạng thái (instance variables), trong khi Interface (trước Java 8) thuần túy định nghĩa hành vi hợp đồng.",
                    "Interface hỗ trợ đa kế thừa cài đặt (multiple implementation) nhưng Abstract Class chỉ cho phép đơn kế thừa.",
                    "Một lớp có thể implement nhiều Interface nhưng chỉ có thể extends một Abstract Class.",
                    "Cả A, B và C đều đúng.",
                    "D",
                    "Tất cả các khẳng định trên đều chính xác về mặt nguyên lý hướng đối tượng trong ngôn ngữ Java.",
                    "medium",
                    "mental_model_gap"
            ));

            result.add(createQuestionMap(
                    "Đoạn mã sau mắc phải bẫy nhận thức nào: 'String s = null; if (s != null & s.length() > 0) { ... }'?",
                    "syntax_swap: Sử dụng toán tử bitwise '&' thay vì toán tử ngắn mạch (short-circuit) '&&', dẫn đến NullPointerException.",
                    "boundary_blindness: Chiều dài chuỗi bắt buộc phải lớn hơn 1 mới hợp lệ.",
                    "mental_model_gap: Biến String trong Java không bao giờ có thể mang giá trị null.",
                    "logic_flaw: Vế kiểm tra s != null luôn trả về true.",
                    "A",
                    "Bẫy syntax_swap kinh điển: Toán tử & không ngắn mạch nên luôn tính toán vế phải s.length() kể cả khi s là null, kích hoạt NullPointerException.",
                    "easy",
                    "syntax_swap"
            ));

            result.add(createQuestionMap(
                    "Khi so sánh hai đối tượng chuỗi trong Java: 'String a = new String(\"ABC\"); String b = new String(\"ABC\"); boolean check = (a == b);', kết quả của 'check' là gì và tại sao?",
                    "true, vì nội dung chuỗi hoàn toàn giống nhau.",
                    "false, vì toán tử '==' so sánh địa chỉ vùng nhớ tham chiếu (reference), không so sánh giá trị nội dung.",
                    "Compile error, vì không thể dùng toán tử '==' cho kiểu đối tượng.",
                    "NullPointerException trong lúc chạy chương trình.",
                    "B",
                    "Bẫy mental_model_gap: Trong Java, toán tử == so sánh tham chiếu. Muốn so sánh giá trị nội dung của String, lập trình viên bắt buộc phải dùng phương thức a.equals(b).",
                    "easy",
                    "mental_model_gap"
            ));
        }

        // 3. Chuyên đề tổng quát / Môn học bất kỳ: Sinh câu hỏi bám sát topicName và promptHint
        if (result.isEmpty() || result.size() < count) {
            String cleanTopic = (topicName != null && !topicName.isBlank()) ? topicName.trim() : "Kiến Thức Chuyên Ngành";
            String hintText = (promptHint != null && !promptHint.isBlank()) ? " (" + promptHint.trim() + ")" : "";

            result.add(createQuestionMap(
                    "Khái niệm cốt lõi nào sau đây phản ánh chính xác nhất bản chất vận hành của chủ đề '" + cleanTopic + "'" + hintText + "?",
                    "Là tập hợp các nguyên lý, mô hình và quy chuẩn được thiết kế để giải quyết bài toán chuyên biệt trong thực tiễn.",
                    "Là quy trình cố định chỉ áp dụng được trên một môi trường duy nhất mà không có tính mở rộng.",
                    "Là phương pháp thuần túy lý thuyết và không có tính ứng dụng trong kỹ thuật hay công nghệ.",
                    "Là công cụ tự động hóa hoàn toàn mà không cần sự kiểm soát logic từ con người.",
                    "A",
                    "Định nghĩa chuẩn mực: Chủ đề '" + cleanTopic + "' cung cấp nền tảng kiến thức và mô hình giải quyết vấn đề có tính ứng dụng cao.",
                    diff,
                    "mental_model_gap"
            ));

            result.add(createQuestionMap(
                    "Khi giải quyết bài toán thuộc lĩnh vực '" + cleanTopic + "'" + hintText + ", lỗi tư duy nào sau đây dễ dẫn đến sai sót khi xử lý điều kiện biên (Boundary Blindness)?",
                    "Chỉ kiểm thử trên tập dữ liệu lý tưởng mà bỏ qua các trường hợp mảng rỗng, giá trị 0 hoặc ngưỡng cực đại.",
                    "Đặt tên biến và hàm theo chuẩn camelCase.",
                    "Sử dụng công cụ kiểm thử tự động (Unit Test).",
                    "Ghi log chi tiết các bước thực thi của hệ thống.",
                    "A",
                    "Boundary Blindness xảy ra khi người học bỏ sót các trường hợp biên đặc biệt như dữ liệu rỗng, cận trên, cận dưới.",
                    "medium",
                    "boundary_blindness"
            ));

            result.add(createQuestionMap(
                    "Phương pháp nào sau đây giúp tối ưu hóa hiệu năng và độ chính xác khi triển khai giải pháp cho '" + cleanTopic + "'?",
                    "Áp dụng quy trình chuẩn hóa dữ liệu, phân tách trách nhiệm module rõ ràng và loại bỏ các bước tính toán dư thừa.",
                    "Tăng gấp đôi số lượng luồng tính toán mà không cần đồng bộ hóa tài nguyên.",
                    "Bỏ qua khâu xử lý ngoại lệ để tăng tốc độ phản hồi.",
                    "Giữ toàn bộ dữ liệu trung gian trên RAM mà không giải phóng sau khi dùng.",
                    "A",
                    "Tối ưu hóa yêu cầu chuẩn hóa kiến trúc, giải phóng tài nguyên và phân chia trách nhiệm logic hợp lý.",
                    diff,
                    "logic_flaw"
            ));

            result.add(createQuestionMap(
                    "Trong bối cảnh thực tế của môn học '" + cleanTopic + "'" + hintText + ", bẫy 'Nhầm lẫn thuật ngữ / cú pháp (Syntax Swap)' thường biểu hiện qua hành vi nào?",
                    "Nhầm lẫn giữa hai khái niệm hoặc cấu trúc lệnh có cách viết tương đồng nhưng ý nghĩa vận hành hoàn toàn trái ngược.",
                    "Đọc kỹ tài liệu đặc tả kỹ thuật trước khi xây dựng chương trình.",
                    "Sử dụng các biến hằng số (constants) để lưu trữ giá trị cấu hình.",
                    "Thực hiện đo đạc độ trễ mạng trước khi truyền tải dữ liệu.",
                    "A",
                    "Syntax Swap là sự nhầm lẫn giữa các từ khóa, toán tử hoặc cú pháp tương tự nhau nhưng có ngữ nghĩa khác biệt.",
                    "easy",
                    "syntax_swap"
            ));

            result.add(createQuestionMap(
                    "Để thẩm định tính đúng đắn của một giải pháp thuộc chuyên đề '" + cleanTopic + "', tiêu chí sư phạm quan trọng nhất là gì?",
                    "Giải pháp phải thỏa mãn cả tính đúng đắn về mặt logic, khả năng chịu lỗi trước dữ liệu bất thường và tuân thủ các quy tắc cốt lõi.",
                    "Chỉ cần chương trình chạy không báo lỗi cú pháp biên dịch.",
                    "Thời gian hoàn thành ngắn nhất bất kể kết quả có sai số.",
                    "Chỉ cần giao diện trực quan, không cần quan tâm tầng xử lý dữ liệu ngầm.",
                    "A",
                    "Tiêu chí thẩm định toàn diện đòi hỏi tính chính xác của thuật toán, độ ổn định trước ngoại lệ và kiến trúc bền vững.",
                    "hard",
                    "mental_model_gap"
            ));
        }

        // Lọc theo misconceptionTag nếu được chỉ định cụ thể
        if (misconceptionTag != null && !misconceptionTag.equalsIgnoreCase("all") && !misconceptionTag.isBlank()) {
            java.util.List<java.util.Map<String, Object>> filtered = new java.util.ArrayList<>();
            for (java.util.Map<String, Object>> q : result) {
                if (misconceptionTag.equalsIgnoreCase((String) q.get("misconceptionTag"))) {
                    filtered.add(q);
                }
            }
            if (!filtered.isEmpty()) {
                result = filtered;
            }
        }

        // Cắt theo số lượng count yêu cầu
        int targetCount = Math.min(Math.max(count, 1), 50);
        if (result.size() > targetCount) {
            return new java.util.ArrayList<>(result.subList(0, targetCount));
        }

        // Nếu thiếu số lượng, nhân bản và biến thể câu hỏi
        while (result.size() < targetCount) {
            int idx = result.size() % Math.max(1, result.size());
            java.util.Map<String, Object> base = new java.util.HashMap<>(result.get(idx));
            base.put("questionText", "[Biến thể " + (result.size() + 1) + "] " + base.get("questionText"));
            result.add(base);
        }

        return result;
    }

    private static java.util.Map<String, Object>> createQuestionMap(
            String text, String a, String b, String c, String d,
            String correct, String explanation, String diff, String misc) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("questionText", text);
        map.put("optionA", a);
        map.put("optionB", b);
        map.put("optionC", c);
        map.put("optionD", d);
        map.put("correctAnswer", correct);
        map.put("explanation", explanation);
        map.put("difficulty", diff);
        map.put("misconceptionTag", misc);
        map.put("source", "Pedagogical Fallback Engine");
        return map;
    }

    /**
     * Phản hồi chatbot tư vấn sư phạm khi AI offline
     */
    public static String generateTeacherChatResponse(String userMessage, String context) {
        return "### 💡 Gợi Ý Sư Phạm Từ Trợ Lý Co-Pilot (Chế độ Thông Minh Nội Bộ)\n\n"
             + "Chào Thầy/Cô! Hệ thống ghi nhận yêu cầu: **\"" + (userMessage != null ? userMessage.trim() : "") + "\"**.\n\n"
             + "Dưới đây là một số đề xuất phương pháp khảo thí chuẩn Bloom:\n"
             + "1. **Định hình ma trận câu hỏi**: Phân bổ 40% Nhận biết, 30% Thông hiểu, 20% Vận dụng và 10% Vận dụng cao.\n"
             + "2. **Cài cắm bẫy nhận thức**: Chú trọng thiết kế phương án nhiễu theo nhóm *Boundary Blindness* (bỏ sót điều kiện biên) và *Mental Model Gap* (hiểu sai mô hình bản chất).\n"
             + "3. **Giải thích sư phạm**: Mỗi phương án sai nên chỉ rõ nguyên nhân học sinh hay chọn nhầm để hỗ trợ bài học củng cố tự động.\n\n"
             + "> *Mẹo: Thầy/Cô có thể bấm vào nút **'Cấu hình API Key'** ở góc phải AI Question Studio để kết nối trực tiếp với Google Gemini 2.0 Flash không giới hạn!*";
    }
}
