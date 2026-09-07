# 🎓 Hệ Thống Học Tập Thông Minh Tích Hợp AI (Intelligent LMS)

> **Đồ Án Cuối Kỳ — Môn Công Nghệ Phần Mềm**  
> Nền tảng LMS hiện đại giúp phát hiện quan niệm sai lầm (Misconception Detection), kích hoạt tính năng **Confidence Tagging** và tự động kiến tạo **Bài học củng cố cá nhân hóa (AI Remedial Lessons)** bằng **Google Gemini 2.5 Flash**.

---

## 🌟 Tính Năng Nổi Bật

1. **Kiểm Tra Trắc Nghiệm Thông Minh & Gắn Nhãn Tự Tin (Confidence Tagging - ADR-009):**
   - Học sinh khi chọn đáp án có thể đánh dấu: 🟢 **Chắc chắn (Certain)** hoặc 🟡 **Đoán mò (Guess)**.
   - Nếu chọn ĐÚNG nhưng với tâm thế **ĐOÁN MÒ**, hệ thống AI vẫn tự động can thiệp để củng cố bản chất lý thuyết, biến kiến thức may rủi thành nền tảng vững vàng.

2. **Chẩn Đoán & Phân Loại Lỗ Hổng Tư Duy (Pedagogical Misconception Diagnosis):**
   - Tự động phân loại lỗi sai vào 4 nhóm:
     - `syntax_swap`: Nhầm lẫn cú pháp đặc tả ngôn ngữ
     - `boundary_blindness`: Bỏ quên điều kiện biên, kiểm tra ranh giới mảng, null pointer
     - `mental_model_gap`: Lỗ hổng mô hình tư duy (OOP, luồng quản lý bộ nhớ Heap/Stack)
     - `logic_flaw`: Lỗi phân nhánh logic điều kiện

3. **Tự Động Sinh Bài Học Củng Cố Cá Nhân Hóa (AI Remedial Lessons - ADR-005):**
   - Ép kiểu định dạng có cấu trúc chuẩn qua Gemini **ResponseSchema (JSON)**.
   - Cung cấp giải thích nguyên nhân, bài học lý thuyết trực quan (có ví dụ code) và 1 câu hỏi thực hành tương đương để học sinh tự kiểm tra lại.

4. **Bộ Đệm Dự Phòng Ngoại Tuyến (AI Fallback Buffer - ADR-008):**
   - Nếu mất mạng, hết quota Gemini API hoặc chưa cấu hình API Key, hệ thống **không bị sập** mà tự động chuyển sang cơ chế Fallback nội bộ từ kho dữ liệu giải thích chuẩn của giảng viên trong CSDL.

5. **Trợ Giảng AI Đa Nhân Cách (Multi-Persona Chatbot Widget - ADR-011):**
   - Widget chat góc phải màn hình 24/7 với 3 persona:
     - 🎓 **Bạn Học Kèm (Peer Tutor):** Thân thiện, mẹo nhớ nhanh
     - 💻 **Senior Developer:** Thực chiến, chuẩn dự án doanh nghiệp
     - 🏛️ **Giáo Sư (Professor):** Học thuật, phân tích nguồn gốc lý thuyết

---

## 🛠️ Bộ Công Nghệ (Tech Stack)

- **Backend:** Java 21+ / Jakarta Servlet 5.0 (`jakarta.servlet.*`)
- **Dev Server:** Eclipse Jetty 11 (`jetty-maven-plugin`) — *Tương thích hoàn hảo với Java 25*
- **Database:** Microsoft SQL Server (SSMS) — Kết nối qua `mssql-jdbc` & HikariCP
- **AI Core:** Google Gemini API (`gemini-2.5-flash`) qua thư viện chính thức `com.google.genai:google-genai:1.64.0`
- **Security:** Mã hóa mật khẩu một chiều BCrypt (Cost 12)
- **Frontend:** Bootstrap 5, FontAwesome 6, SweetAlert2, Highlight.js, Marked.js (CDN-first)

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Hệ Thống

### 1. Khởi Tạo Cơ Sở Dữ Liệu trong SSMS
1. Mở **SQL Server Management Studio (SSMS)** và kết nối vào SQL Server local của bạn.
2. Mở file script: `src/main/resources/db/schema.sql`.
3. Nhấn **Execute (F5)** để tự động tạo CSDL `lms_db`, 7 bảng chuẩn 3NF và 10 câu hỏi mẫu tiếng Việt kèm giải thích.

### 2. Cấu Hình Biến Môi Trường (`.env`)
Tạo một file `.env` tại thư mục gốc của dự án (copy từ `.env.example`):
```ini
# Cấu hình CSDL SQL Server Local
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=lms_db;trustServerCertificate=true;encrypt=true
DB_USERNAME=sa
DB_PASSWORD=your_password_here
DB_POOL_SIZE=10

# Cấu hình Google Gemini API
# Lấy API Key miễn phí tại: https://aistudio.google.com
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-2.5-flash

# Port máy chủ
PORT=8080
```
> *(Lưu ý: Nếu chưa nhập `GEMINI_API_KEY`, hệ thống sẽ tự động kích hoạt chế độ Fallback Buffer mà không báo lỗi!)*

### 3. Biên Dịch & Chạy Ứng Dụng (Không Cần Cài Maven)
Dự án đã tích hợp sẵn **Maven Wrapper**. Bạn chỉ cần mở PowerShell hoặc Command Prompt tại thư mục dự án:

```powershell
# Biên dịch toàn bộ dự án:
.\mvnw.cmd compile

# Hoặc bằng PowerShell script:
.\mvnw.ps1 compile

# Khởi động Web Dev Server (Jetty 11):
.\mvnw.cmd jetty:run
```

### 4. Truy Cập Ứng Dụng
Mở trình duyệt và truy cập:
👉 **`http://localhost:8080/`** (hoặc `http://localhost:8080/index.html`)

- Bạn có thể chuyển sang tab **Đăng Ký** để tạo tài khoản sinh viên mới (kèm nhập sở thích như bóng đá, anime để AI tạo ẩn dụ thân thuộc).
- Hoặc đăng nhập ngay và bắt đầu làm bài trắc nghiệm!

---

## 📂 Cấu Trúc Mã Nguồn

```
Hệ thống học tập thông minh/
├── .mvn/wrapper/                  # Maven Wrapper tự động chạy mọi máy
├── mvnw & mvnw.cmd & mvnw.ps1     # Script thực thi Maven
├── pom.xml                        # Cấu hình Jetty 11, MSSQL JDBC, Gemini SDK
├── .env.example                   # Mẫu cấu hình môi trường
├── src/
│   └── main/
│       ├── java/com/lms/
│       │   ├── model/             # POJO (User, Topic, Question, QuizSession, UserAnswer, RemedialLesson, ChatMessage)
│       │   ├── dao/               # JDBC Data Access (HikariCP, SQL Server)
│       │   ├── service/           # AIService (Gemini SDK), QuizService, UserService, PromptBuilder, FallbackService
│       │   ├── servlet/           # AuthServlet, QuizServlet, TopicServlet, ChatServlet
│       │   ├── filter/            # CorsFilter, AuthFilter
│       │   └── util/              # ConfigLoader, JsonHelper
│       ├── resources/
│       │   └── db/schema.sql      # Kịch bản CSDL SQL Server 3NF tiếng Việt
│       └── webapp/
│           ├── css/app.css        # Giao diện hiện đại, nút chatbot nổi
│           ├── js/                # api.js, chat-widget.js
│           ├── index.html         # Bảng điều khiển & danh mục chủ đề
│           ├── auth.html          # Đăng nhập / Đăng ký
│           ├── quiz.html          # Làm bài thi + Confidence Tagging
│           ├── result.html        # Phân tích kết quả + Bài học củng cố AI
│           └── history.html       # Lịch sử thi & biểu đồ tiến độ
└── README.md
```
