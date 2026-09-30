# 📋 PROJECT_STATE.md — AI Context Ledger

> **Mục đích:** File này duy trì ngữ cảnh giữa các phiên làm việc. Cuối mỗi phiên, AI cập nhật file này để phiên sau bắt kịp ngay lập tức.

---

## Current Phase
**Giai đoạn 9 ✅ HOÀN THÀNH — Bảo Mật & Ổn Định (Security & Resilience Audit) & Vận Hành 24/7 (UptimeRobot Keep-Alive)**

## Completed Milestones
- [x] **Phase 0:** Thiết lập nền móng kiến trúc, 12 ADRs, schema.sql 7 bảng 3NF cho SQL Server, pom.xml cấu hình Jetty 11 & Gemini SDK v1.64.0.
- [x] **Maven Wrapper:** Khởi tạo và tối ưu hóa `mvnw`, `mvnw.cmd`, `mvnw.ps1` hỗ trợ hoàn hảo thư mục có dấu tiếng Việt trên Windows.
- [x] **Phase 1 (Data Layer):**
  - Models POJO: `User`, `Topic`, `Question`, `QuizSession`, `UserAnswer` (có trường `confidenceLevel`), `RemedialLesson`, `ChatMessage`.
  - Utils: `ConfigLoader` (đọc `.env`), `JsonHelper` (Gson với UTF-8 và `LocalDateTime` adapter).
  - DAOs: `DatabaseUtil` (HikariCP connection pool), `UserDAO`, `TopicDAO`, `QuestionDAO`, `QuizDAO`, `ChatDAO`.
- [x] **Phase 2 (Service Layer & Controllers):**
  - Services: `UserService` (BCrypt cost 12), `QuizService` (chấm điểm, điều phối AI và lưu kết quả).
  - Filters: `CorsFilter` (CORS headers, preflight OPTIONS), `AuthFilter` (bảo vệ endpoint).
  - Servlets: `AuthServlet` (`/api/auth/*`), `TopicServlet` (`/api/topics/*`), `QuizServlet` (`/api/quiz/*`), `ChatServlet` (`/api/chat/*`).
- [x] **Phase 3 (AI Core & Fallback Buffer):**
  - `AIService`: Tích hợp Google Gemini API (`gemini-3.6-flash`), cấu hình structured JSON output.
  - `FallbackService`: Kích hoạt cơ chế ngoại tuyến (ADR-008 AI Fallback Buffer) từ giải thích gốc trong CSDL khi mất mạng hoặc chưa cấu hình API key.
  - `PromptBuilder`: Thiết kế prompt sư phạm phát hiện quan niệm sai lầm (`syntax_swap`, `boundary_blindness`, `mental_model_gap`, `logic_flaw`) và hỗ trợ Confidence Tagging.
- [x] **Phase 4 (Frontend UI & Chatbot Widget):**
  - `auth.html`: Đăng nhập & Đăng ký (hỗ trợ nhập sở thích cá nhân hóa).
  - `index.html`: Bảng điều khiển danh mục chủ đề học tập.
  - `quiz.html`: Giao diện thi trắc nghiệm kèm bộ chọn **Confidence Tagging** (Chắc chắn vs Đoán mò).
  - `result.html`: Trung tâm kết quả & Bài học củng cố cá nhân hóa do AI tạo.
  - `history.html`: Lịch sử các lần thi và thống kê điểm số.
  - `chat-widget.js`: Trợ giảng AI nổi đa nhân cách (Senior Dev, Peer Tutor, Professor) tích hợp trên toàn bộ trang.
- [x] **Phase 5A (Tối Ưu UI Quiz & Teacher Dashboard):**
  - UI Quiz: Sửa hover đáp án `#252a36`, viền `#6366f1`, transition `0.12s`, chữ luôn trắng `#fff`.
  - Định dạng Code: Tích hợp Marked.js + Highlight.js (Atom One Dark theme) tự động định dạng mã nguồn `pre`/`code` trong câu hỏi và bài học.
  - Phân quyền & Điều hướng: `AuthServlet` trả role, tự động chuyển hướng Giảng viên sang `teacher-dashboard.html`, chặn sinh viên truy cập.
  - Giao diện Giảng viên (`teacher-dashboard.html` & `js/teacher.js`): 4 Thẻ KPI, Quản lý ngân hàng câu hỏi, AI Pedagogical Insight.
  - Backend APIs: Bật `QuestionServlet.java` (`/api/questions`) và `TeacherServlet.java` (`/api/teacher/stats`).
- [x] **Phase 5B (Tối Ưu Chi Tiết UX & Adaptive Remediation):**
  - Tự động lưu tiến độ (Autosave Progress) vào `localStorage`, Fullscreen AI Loading Overlay, Guard Routes & SweetAlert2 Toasts.
  - Teacher Dashboard Nâng Cao (tìm kiếm realtime, phân trang 10 câu/trang).
  - Adaptive Remediation Engine (Mini-Quiz 3 câu, Confetti, huy hiệu "ĐÃ PHỤC HỒI KIẾN THỨC").
- [x] **Phase 6 (Chuyển Đổi PostgreSQL Neon.tech & Đóng Gói Docker & Deploy Cloud):**
  - `pom.xml`: Chuyển compile target sang Java 17 LTS, thêm `org.postgresql:postgresql:42.7.2`.
  - `DatabaseUtil.java`: Tự động nhận diện Driver (PostgreSQL/MSSQL), PL/pgSQL `DO $$` auto-migration, auto-seed từ `schema.sql` nếu CSDL trống.
  - DAOs (`QuestionDAO`, `QuizDAO`, `UserDAO`, `TopicDAO`, `ChatDAO`): Chuẩn hóa 100% dialect PostgreSQL và thay thế `getNString`/`setNString` sang `getString`/`setString`.
  - `schema.sql`: Chuẩn hóa PostgreSQL DDL (`SERIAL PRIMARY KEY`, `VARCHAR`, `TEXT`, `BOOLEAN`, UTF-8, sequence an toàn).
  - `Dockerfile` & `.dockerignore`: Multi-stage build (`maven:3.9.6-eclipse-temurin-17-alpine` -> `jetty:11-jre17-alpine` port 8080).
  - `AIService.java`: Nâng cấp model thế hệ mới `gemini-3.6-flash`, cơ chế tự động fallback giữa model `gemini-3.6-flash` và `gemini-3.8-flash`, chẩn đoán lỗi chi tiết và Smart Offline FAQ Fallback.
  - **Triển khai Cloud Production (Render + Neon.tech PostgreSQL):** Đã kiểm thử trực tiếp thành công 100% trên Render live URL `https://cnpm-ck-intelligent-learning-system.onrender.com` với API Key Google AI Studio. Trợ Giảng AI phản hồi mượt mà, phân tích sư phạm chuẩn xác!
- [x] **Phase 7 (Trợ Lý AI Giảng Viên — AI Question Studio & Teacher Co-Pilot):**
  - `PromptBuilder.java`: System prompts sư phạm chuyên gia đo lường khảo thí và tạo câu hỏi cấu trúc JSON theo thang Bloom.
  - `AIService.java`: Tích hợp `generateQuestionsForTeacher(...)` và `teacherChat(...)` với `gemini-3.6-flash` (fallback `gemini-3.8-flash`).
  - `TeacherServlet.java`: Mở các endpoints bảo mật `POST /api/teacher/ai/generate` và `POST /api/teacher/ai/chat` (chỉ cho phép `TEACHER` và `ADMIN`).
  - `teacher-dashboard.html`: Tab 3 "Trợ Lý AI Soạn Đề & Bài Tập" với Studio sinh đề phân hóa + Khung chat Co-Pilot Sư Phạm.
  - `js/teacher.js` & `js/api.js`: Tự động nạp chủ đề, render thẻ câu hỏi định dạng code Java (Highlight.js + Marked.js), 1-Click Import từng câu vào database PostgreSQL, Batch Import toàn bộ vào ngân hàng đề, mở Modal tùy biến câu hỏi trước khi lưu.
- [x] **Phase 8 (Tối Ưu Trải Nghiệm & Đồng Bộ Toàn Diện Tài Liệu /learn):**
  - `auth.html`: Tích hợp nút toggle con mắt (hiện / ẩn mật khẩu) cho cả form Đăng nhập và Đăng ký với CSS chuyên dụng `.toggle-password-btn`.
  - `teacher-dashboard.html` & `teacher.js`: Xóa nút "Soạn Bằng AI" dư thừa ở Tab 1 theo yêu cầu người dùng, tập trung toàn bộ nghiệp vụ AI vào Tab 3 Studio.
  - `QuizDAO.java`: Sửa lỗi cú pháp PostgreSQL (`ISNULL` -> `COALESCE`), bọc try-catch độc lập cho từng KPI để bảo đảm 4 thẻ số liệu trên Teacher Dashboard luôn hoạt động trơn tru.
  - `export_codebase.ps1` & `FULL_CODEBASE.md`: Tự động trích xuất toàn bộ 47 file mã nguồn của dự án thành 1 file duy nhất để phục vụ đánh giá, thẩm định từ bên thứ ba.
  - `README.md`: Nâng cấp toàn diện với sơ đồ kiến trúc Mermaid, bảng công nghệ chi tiết, nguyên lý sư phạm và luồng dữ liệu liên kết giữa các tầng.
  - Thiết lập đề xuất quy chuẩn `/learn` tự động đồng bộ 3 file tài liệu sau mỗi phiên thay đổi mã nguồn.
- [x] **Phase 9 (Nhánh Thử Nghiệm `refactor-experiment` — Security & Resilience Audit):**
  - Vá lỗ hổng IDOR/Object-level authorization tại `GET /api/quiz/session/{id}`.
  - Khóa tính toàn vẹn nộp bài thi (chống nộp 2 lần, chống trùng/thiếu câu hỏi, bọc kiểm tra topic).
  - Tầng vận chuyển mạng `js/api.js` nâng cao với timeout, AbortController, offline detection và safe retry; bổ sung `js/ui.js` xử lý safe Markdown/HTML sanitation chống XSS.
  - Sửa lỗi biên dịch `HttpServletResponse.SC_UNPROCESSABLE_ENTITY` (thay bằng `422` cho tương thích Jakarta Servlet 5.0).
  - Schema CSDL bổ sung Unique index `(session_id, question_id)` chống duplicate answers.
  - Giữ nguyên vẹn nhánh `main` để bảo toàn fallback production.
- [x] **Phase 10 (Nhánh `dev-backend` — Open Peer-Review Forum & Unlimited Multi-Disciplinary Question Engine):**
  - **Đăng ký đa vai trò (Multi-Role Registration)**: Mọi người dùng đều có thể tự do đăng ký với role `teacher` (người sáng tạo/chia sẻ đề) hoặc `student`, phá bỏ giới hạn 1 tài khoản giảng viên duy nhất.
  - **Chuẩn hóa điểm số thang 10**: Sửa triệt để lỗi hiển thị `37.5 / 10` do nhầm lẫn thang % 100 điểm với thang 10. `QuizService` và `QuizDAO` chuẩn hóa lưu trữ và truy vấn KPI thang 10 (ví dụ `3.8 / 10`), tương thích ngược hoàn hảo với dữ liệu cũ.
  - **Động hóa chủ đề & Ngân hàng câu hỏi liên môn vô hạn**: `TopicDAO.findOrCreate` và `POST /api/topics` cho phép người dùng tự tạo bất kỳ môn học hay chủ đề nào (Toán, Lý, Kinh tế, Lập trình hỗn hợp). AI Prompt Builder được phổ quát hóa không giới hạn môn học, tăng trần sinh câu hỏi từ 5 lên đến 25 câu.
  - **Diễn đàn Thảo luận & Hệ thống Đánh giá Độ Tin Cậy (Forum & Credibility Engine)**: Bổ sung 2 bảng `question_comments` và `question_ratings` (kèm auto-migration hỗ trợ cả Neon.tech PostgreSQL lẫn SQL Server). Cung cấp đầy đủ API bình luận, thảo luận phản biện, Upvote/Downvote, tính % điểm tín nhiệm và Báo lỗi ảo giác AI để cộng đồng cùng kiểm duyệt chất lượng câu hỏi.

## Pending Tasks (Các bước tiếp theo mở rộng)
- [ ] Xây dựng giao diện Frontend (nhánh `dev-frontend`) cho Diễn đàn thảo luận và Vote tín nhiệm dưới mỗi câu hỏi.
- [ ] Nâng cấp giao diện Teacher Dashboard: Thêm nút/modal tạo chủ đề tự do, gỡ bỏ giới hạn selectbox 2 môn, tăng slider số lượng câu hỏi lên 25.
- [ ] Cập nhật giao diện Đăng ký (`auth.html`) cho phép chọn vai trò Giảng viên / Người chia sẻ kiến thức.
- [ ] Export báo cáo thống kê kết quả học tập ra Excel/PDF cho Giảng viên.

---

## Architecture Decisions (ADR)

| # | Quyết định | Lý do |
|---|---|---|
| ADR-001 | MySQL → **SQL Server (SSMS)** | Tương thích hạ tầng local, HeidiSQL/SSMS quản lý |
| ADR-002 | `tomcat7-maven-plugin` → **Jetty 11** (`jetty-maven-plugin`) | Tomcat7 crash Java 25. Jetty 11 hỗ trợ Java 11+ |
| ADR-003 | `javax.servlet.*` → **`jakarta.servlet.*`** (Jakarta Servlet 5.0) | Jetty 11 yêu cầu Jakarta EE namespace |
| ADR-004 | Compiler target **Java 21** (runtime Java 25) | Java 21 = LTS gần nhất, tối đa tương thích |
| ADR-005 | Gemini SDK **ResponseSchema** (Structured Output) | Ép trả JSON nguyên bản, zero parse error |
| ADR-006 | **NVARCHAR(MAX/255)** cho mọi cột text | Unicode tiếng Việt |
| ADR-007 | **CHECK constraint** thay ENUM | SQL Server không có ENUM |
| ADR-008 | `explanation` trong `questions` = **AI Fallback Buffer** | Decoupling: hiển thị giải thích khi API offline |
| ADR-009 | `confidence_level` (CERTAIN/GUESS) trong `user_answers` | Confidence Tagging: AI củng cố câu đoán mò |
| ADR-010 | Gemini SDK **v1.64.0** (stable 07/2026) | Latest, hỗ trợ ResponseSchema + async |
| ADR-011 | **Template-First Policy** — Bootstrap 5 + Floating Chat Widget | Không viết UI from scratch; chỉ Data Binding |
| ADR-012 | CDN cho FontAwesome, SweetAlert2, Highlight.js, Marked.js | Nhẹ, không cài local, luôn cập nhật |
| ADR-013 | **Git Branching Strategy** (`main`, `refactor-experiment`, `dev-backend`, `dev-frontend`) | Bảo toàn fallback `main` và audit 3rd party `refactor-experiment`; phát triển độc lập backend rồi frontend |
| ADR-014 | **24/7 Cloud Availability** (Render + UptimeRobot Keep-Alive) | Khắc phục Spin-down 15p của Render free tier, đảm bảo 744h/tháng luôn online tức thì |
| ADR-015 | **Universal Multi-Disciplinary Dynamic Topics** | Loại bỏ hardcode môn học, tự động sinh và liên kết Topic trong CSDL khi người dùng nhập bất kỳ chuyên ngành nào |
| ADR-016 | **Crowdsourced Credibility & Peer Review Model** | Bảng `question_ratings` và `question_comments` tạo cơ chế phản biện xã hội, thanh lọc ảo giác AI dựa trên trí tuệ đám đông |

---

*Cập nhật lần cuối: 2026-09-30 (GMT+7)*
