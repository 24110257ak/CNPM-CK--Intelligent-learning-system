---
description: Quy chuẩn bắt buộc đồng bộ hóa tài liệu dự án và toàn bộ mã nguồn sau mỗi phiên thay đổi
globs: ["**/*"]
alwaysApply: true
---

# Quy Chuẩn Đồng Bộ Tài Liệu & Toàn Bộ Mã Nguồn Dự Án

Sau mỗi phiên làm việc có chỉnh sửa, refactor hoặc bổ sung tính năng vào mã nguồn (`src/`, `pom.xml`, `Dockerfile`, ...), trợ lý AI BẮT BUỘC phải rà soát và cập nhật đồng bộ 3 tài liệu trọng tâm sau:

## 1. `README.md` (Tài liệu kiến trúc & vận hành dự án)
- **Công nghệ cốt lõi:** Luôn nêu rõ và cập nhật công nghệ thực tế đang sử dụng:
  - Backend: Java 17/21, Jakarta EE 9 Servlet & Filter, Eclipse Jetty 11 Embedded Server.
  - CSDL & Connection Pool: PostgreSQL (Neon.tech Cloud / Render), HikariCP (với cơ chế Auto-Seed & Auto-Migration).
  - Trí tuệ nhân tạo (AI): Google Gemini API (Model `gemini-3.6-flash`), Circuit Breaker, Dual-Model Fallback & Smart FAQ Offline Buffer.
  - Đóng gói & Triển khai: Multi-stage Dockerfile (`maven:3.9.6-eclipse-temurin-17-alpine` -> `jetty:11-jre17-alpine`), Cloud-ready Render.
  - Frontend: Vanilla JS (ES6+), Bootstrap 5.3, FontAwesome 6, SweetAlert2, Highlight.js, Marked.js, Canvas-Confetti.
- **Nguyên lý hoạt động:** Trình bày rõ nguyên lý sư phạm (Confidence Tagging, Misconception Diagnosis 4 nhóm, Adaptive Remediation Mini-Quiz, AI Pedagogical Insight, Multi-Persona Tutor Chatbot).
- **Sự liên kết giữa các công nghệ (Interconnection):** Thể hiện rõ sơ đồ kiến trúc luồng dữ liệu (Dataflow & Layered Architecture: Client -> Servlet Controller -> Service Layer -> DAO Layer -> HikariCP -> Neon PostgreSQL; Service Layer <-> Gemini 3.6 Flash API).

## 2. `PROJECT_STATE.md` (Sổ tay trạng thái phiên làm việc)
- Cập nhật trạng thái Phase hiện tại, các tính năng vừa hoàn thành, quyết định kiến trúc (ADR bổ sung nếu có) và các task tồn đọng.

## 3. `FULL_CODEBASE.md` (Gom toàn bộ mã nguồn phục vụ đánh giá)
- Chạy script `powershell -ExecutionPolicy Bypass -File .\export_codebase.ps1` để tự động gom toàn bộ các file mã nguồn mới nhất vào `FULL_CODEBASE.md`.
