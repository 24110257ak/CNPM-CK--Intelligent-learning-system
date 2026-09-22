# Refactor & Resilience Audit — 2026-09-22

## 5 điểm yếu nghiêm trọng nhất đã phát hiện

1. **IDOR/Object-level authorization ở Quiz Session**: `GET /api/quiz/session/{id}` trước đây chỉ yêu cầu đăng nhập, không xác minh `session_id` thuộc user hiện tại. Điều này có thể làm lộ đáp án/lịch sử học của user khác nếu đoán được ID.
2. **Integrity của luồng submit chưa được khóa**: backend chưa chặn nộp lại cùng session, question ID trùng, question thuộc topic khác, hoặc số câu trả lời không khớp. Điều này làm sai dữ liệu điểm và analytics.
3. **API client thiếu resilience**: `fetch` không timeout, không AbortController, không phân loại offline/network/HTTP/invalid JSON và không có retry có kiểm soát. Khi API treo hoặc mạng chập chờn UX có thể bị kẹt.
4. **Frontend có bề mặt XSS lớn**: nhiều dữ liệu động/Markdown được render bằng `innerHTML`/`marked.parse` trực tiếp. Đã bổ sung shared sanitizer và chuyển các điểm Markdown chính sang renderer an toàn.
5. **CORS credentialed reflection không an toàn**: server phản chiếu bất kỳ `Origin` nào đồng thời bật `Access-Control-Allow-Credentials: true`. Đã đổi sang same-origin mặc định + allowlist cấu hình rõ bằng `CORS_ALLOWED_ORIGINS`.

## Thay đổi kiến trúc/logic

- Thêm `js/ui.js` làm shared presentation utility: escape/sanitize HTML, safe Markdown, busy state, ARIA live announcements.
- Viết lại `js/api.js` thành resilient transport layer với `ApiError`, timeout, abort, offline detection, invalid JSON protection và GET retry có giới hạn.
- Tăng ownership validation ở `QuizDAO`/`QuizService` và Chat session attachment.
- Thêm unique index `(session_id, question_id)` để database trở thành lớp phòng thủ cuối chống duplicate answer.
- Giới hạn JSON request body 256 KiB; giới hạn chat message 4.000 ký tự và normalize persona.
- CORS theo allowlist; bổ sung CSP, `nosniff`, Referrer Policy, Permissions Policy.

## UI/UX & Accessibility

- Design layer mới: spacing/card/form/button thống nhất, responsive mobile, focus-visible rõ, reduced-motion support.
- Có primitive cho Skeleton/Empty state và ARIA live region.
- Chat widget responsive hơn trên mobile; table/modal và layout được làm mềm theo breakpoint.
- Safe Markdown vẫn giữ Highlight.js/Marked.js nhưng loại bỏ script/event-handler/javascript URL trước khi đưa vào DOM.

## Kiểm thử thực hiện

- `node --check` đã chạy thành công cho `api.js`, `ui.js`, `quiz.js`, `result.js`, `teacher.js`, `chat-widget.js`.
- Maven build chưa thể chạy hoàn tất trong sandbox vì Maven Wrapper cần tải distribution/dependencies từ `repo.maven.apache.org`, trong khi runtime hiện tại không có network egress. Đây là hạn chế môi trường kiểm thử, không phải lỗi build đã xác nhận của source.

## Khuyến nghị tiếp theo

- Tách `teacher.js`, `result.js`, `quiz.js` thành ES modules theo feature (state/view/service/controller) nếu dự án tiếp tục phát triển lớn.
- Thay `System.err`/`printStackTrace()` tại DAO bằng SLF4J + error code/correlation ID thống nhất.
- Bọc submit quiz trong **một DB transaction** để session completion, answers và remedial lessons atomic; hiện tại vẫn có khả năng partial-write nếu DB/AI lỗi giữa luồng.
- Thêm JUnit 5 + Testcontainers PostgreSQL cho authorization/integrity regression tests; thêm Playwright cho luồng login → quiz → submit → result.
