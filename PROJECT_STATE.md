# 📋 PROJECT_STATE.md — AI Context Ledger

> **Mục đích:** File này duy trì ngữ cảnh giữa các phiên làm việc. Cuối mỗi phiên, AI cập nhật file này để phiên sau bắt kịp ngay lập tức.

---

## Current Phase
**Giai đoạn 0 ✅ HOÀN THÀNH — Thiết Lập Nền Móng (Foundation Setup)**

## Completed Milestones
- [x] Tài liệu tư vấn công nghệ LMS đã đọc & phân tích (10 trang PDF)
- [x] Implementation Plan **v2** đã tạo — phản ánh đầy đủ:
  - SQL Server (SSMS) thay MySQL
  - Jetty 11 thay Tomcat7 (Java 25 compatible)
  - Jakarta Servlet 5.0 (`jakarta.servlet.*`)
  - ResponseSchema structured JSON output
  - AI Fallback Buffer
  - Confidence Tagging
  - **Template-First Policy** (AdminLTE 4 + Vanilla Chat Widget)
- [x] ERD & Schema SQL Server chuẩn 3NF (7 bảng + seed data tiếng Việt 10 câu hỏi)
- [x] `pom.xml` hoàn chỉnh (Jetty 11, MSSQL JDBC 12.8.1, Gemini SDK v1.64.0, HikariCP, BCrypt, dotenv-java)
- [x] `.gitignore` + `.env.example` + `web.xml` (Jakarta EE 5.0)
- [x] PROJECT_STATE.md (file này) đã khởi tạo

## Pending Tasks
- [ ] **Cài đặt môi trường:** Maven, SQL Server (SSMS), HeidiSQL, Gemini API Key
- [ ] **Giai đoạn 1:** Model → DAO → Service → DatabaseUtil → ConfigLoader
- [ ] **Giai đoạn 2:** Servlet Controllers + Filters + QuizService
- [ ] **Giai đoạn 3:** AIService (Gemini SDK + ResponseSchema) + FallbackService + PromptBuilder
- [ ] **Giai đoạn 4:** Download AdminLTE 4 + Chat Widget → tích hợp + Data Binding
- [ ] **Giai đoạn 5:** Test E2E + Git push

## Architecture Decisions (ADR)

| # | Quyết định | Lý do |
|---|---|---|
| ADR-001 | MySQL → **SQL Server (SSMS)** | Tương thích hạ tầng local, HeidiSQL quản lý |
| ADR-002 | `tomcat7-maven-plugin` → **Jetty 11** (`jetty-maven-plugin`) | Tomcat7 crash Java 25. Jetty 11 hỗ trợ Java 11+ |
| ADR-003 | `javax.servlet.*` → **`jakarta.servlet.*`** (Jakarta Servlet 5.0) | Jetty 11 yêu cầu Jakarta EE namespace |
| ADR-004 | Compiler target **Java 21** (runtime Java 25) | Java 21 = LTS gần nhất, tối đa tương thích |
| ADR-005 | Gemini SDK **ResponseSchema** (Structured Output) | Ép trả JSON nguyên bản, zero parse error |
| ADR-006 | **NVARCHAR(MAX/255)** cho mọi cột text | Unicode tiếng Việt |
| ADR-007 | **CHECK constraint** thay ENUM | SQL Server không có ENUM |
| ADR-008 | `explanation` trong `questions` = **AI Fallback Buffer** | Decoupling: hiển thị giải thích khi API offline |
| ADR-009 | `confidence_level` (CERTAIN/GUESS) trong `user_answers` | Confidence Tagging: AI củng cố câu đoán mò |
| ADR-010 | Gemini SDK **v1.64.0** (stable 07/2026) | Latest, hỗ trợ ResponseSchema + async |
| ADR-011 | **Template-First Policy** — AdminLTE 4 + Vanilla Chat Widget | Không viết UI from scratch; chỉ Data Binding |
| ADR-012 | CDN cho FontAwesome, SweetAlert2, Chart.js, Highlight.js | Nhẹ, không cài local, luôn cập nhật |

## Next Action Prompt
```
Tiếp tục Giai đoạn 1: Tạo Java skeleton project.

TRƯỚC KHI CODE, cần xác nhận với user:
1. Đã cài Maven chưa? (chạy: mvn -version)
2. Đã cài SQL Server + SSMS chưa?
3. Đã lấy Gemini API Key chưa?

SAU KHI XÁC NHẬN:
1. Tạo Model classes (POJO): User, Topic, Question, QuizSession, UserAnswer (có confidenceLevel), RemedialLesson, ChatMessage
2. Tạo ConfigLoader.java (đọc .env bằng dotenv-java)
3. Tạo DatabaseUtil.java (HikariCP + SQL Server)
4. Tạo DAO layer: UserDAO, TopicDAO, QuestionDAO, QuizDAO, ChatDAO
5. Chạy: mvn clean compile → xác nhận build thành công
6. Chạy schema.sql trong SSMS → xác nhận DB sẵn sàng
```

---

*Cập nhật lần cuối: 2026-09-07 13:35 (GMT+7) — Phiên 1*
