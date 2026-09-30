# 🎓 Hệ Thống Học Tập Thông Minh Tích Hợp AI (Intelligent LMS)

> **Đồ Án Cuối Kỳ — Môn Công Nghệ Phần Mềm (CNPM - CK)**  
> Nền tảng LMS hiện đại kết hợp Trí tuệ Nhân tạo thế hệ mới (**Google Gemini 3.6 Flash**), ứng dụng các lý thuyết sư phạm thực nghiệm: **Confidence Tagging** (Gắn nhãn độ tự tin), **Pedagogical Misconception Detection** (Chẩn đoán 4 nhóm lỗ hổng tư duy), **Adaptive Remediation Engine** (Động cơ củng cố kiến thức thích ứng) và **Teacher AI Co-Pilot Studio** (Studio trợ lý soạn đề & bài tập thông minh cho giảng viên).

---

## 🏛️ 1. Kiến Trúc Hệ Thống & Sự Liên Kết Công Nghệ (Architecture & Interconnections)

Hệ thống được thiết kế theo kiến trúc phân tầng chuẩn doanh nghiệp (**Layered Architecture / MVC - Service - DAO**), tối ưu hóa hiệu năng, tính mở rộng và khả năng phục hồi (Resilience):

```mermaid
graph TD
    subgraph Client_Layer ["Client Layer (Trình Duyệt Người Dùng)"]
        UI_Student["Giao Diện Sinh Viên<br/>(index.html, quiz.html, result.html)"]
        UI_Teacher["Giao Diện Giảng Viên<br/>(teacher-dashboard.html)"]
        UI_Auth["Xác Thực & Đăng Nhập<br/>(auth.html)"]
        JS_Client["Vanilla JS ES6+ Modules<br/>(api.js, quiz.js, result.js, teacher.js)"]
    end

    subgraph Container_Runtime ["Runtime Container (Docker / Eclipse Jetty 11)"]
        subgraph Controller_Layer ["Controller Layer (Jakarta Servlet 5.0)"]
            AuthServlet["AuthServlet<br/>/api/auth/*"]
            QuizServlet["QuizServlet<br/>/api/quiz/*"]
            TeacherServlet["TeacherServlet<br/>/api/teacher/*"]
            QuestionServlet["QuestionServlet<br/>/api/questions/*"]
            ChatServlet["ChatServlet<br/>/api/chat/*"]
            Filter["CorsFilter & AuthFilter"]
        end

        subgraph Service_Layer ["Service Layer (Business Logic & AI Orchestration)"]
            QuizService["QuizService<br/>(Chấm điểm, phân tích bài thi)"]
            UserService["UserService<br/>(BCrypt hashing, quản lý tài khoản)"]
            AIService["AIService<br/>(Điều phối Gemini API, Circuit Breaker)"]
            FallbackService["FallbackService<br/>(Bộ đệm cứu sinh offline)"]
            PromptBuilder["PromptBuilder<br/>(Xây dựng Prompt sư phạm chuẩn JSON)"]
        end

        subgraph DAO_Layer ["Data Access Layer (JDBC & Connection Pool)"]
            DatabaseUtil["DatabaseUtil (HikariCP Pool)"]
            UserDAO["UserDAO"]
            QuizDAO["QuizDAO"]
            QuestionDAO["QuestionDAO"]
            TopicDAO["TopicDAO"]
            ChatDAO["ChatDAO"]
        end
    end

    subgraph Infrastructure_Cloud ["Hạ Tầng Điện Toán Đám Mây & AI"]
        NeonDB[("PostgreSQL 16 Cloud<br/>(Neon.tech Serverless / Render)")]
        GeminiAPI["Google Gemini 3.6 Flash<br/>(Google AI Studio REST API)"]
        DockerHost["Render Cloud Web Service<br/>(Container hóa Multi-stage)"]
    end

    %% Client to Controller
    UI_Student -->|RESTful JSON / Fetch API| Filter
    UI_Teacher -->|RESTful JSON / Fetch API| Filter
    UI_Auth -->|RESTful JSON / Fetch API| Filter
    Filter --> Controller_Layer

    %% Controller to Service
    AuthServlet --> UserService
    QuizServlet --> QuizService
    TeacherServlet --> QuizDAO
    TeacherServlet --> AIService
    ChatServlet --> AIService
    QuestionServlet --> QuestionDAO

    %% Service to Service & AI
    QuizService --> AIService
    AIService -.->|Khi mất mạng / Hết quota| FallbackService
    AIService -->|Structured JSON Output| GeminiAPI
    AIService --> PromptBuilder

    %% Service to DAO
    QuizService --> QuizDAO
    UserService --> UserDAO
    FallbackService --> QuestionDAO

    %% DAO to Database
    DAO_Layer --> DatabaseUtil
    DatabaseUtil -->|HikariCP Connection Pool| NeonDB
    Container_Runtime -.->|Triển khai trên| DockerHost
```

---

## 🛠️ 2. Bảng Tổng Hợp Công Nghệ & Vai Trò (Tech Stack Specification)

| Thành Phần (Layer) | Công Nghệ Sử Dụng | Phiên Bản | Vai Trò & Lý Do Lựa Chọn |
|---|---|---|---|
| **Core Platform** | **Java (JDK)** | 17 LTS / 21 LTS | Nền tảng hướng đối tượng mạnh mẽ, an toàn kiểu dữ liệu, bảo mật bộ nhớ và hiệu năng xử lý cao. |
| **Web Server / Servlet Engine** | **Eclipse Jetty** | 11.0.24 | Nhẹ hơn Tomcat rất nhiều, thời gian khởi động < 2 giây, tương thích hoàn hảo với Java 17/21 và Jakarta EE 9. |
| **Servlet Specification** | **Jakarta Servlet & Filter** | 5.0.0 (`jakarta.*`) | Định tuyến RESTful API, kiểm soát phiên đăng nhập (`HttpSession`), lọc CORS và phân quyền vai trò (`AuthFilter`). |
| **Cơ Sở Dữ Liệu (Database)** | **PostgreSQL** | 16 (Neon.tech / Render) | CSDL quan hệ chuẩn ACID, lưu trữ JSON linh hoạt, điện toán Serverless trên Cloud, tự động co giãn kết nối. |
| **Connection Pooling** | **HikariCP** | 5.1.0 | Thư viện quản lý kết nối CSDL nhanh nhất thế giới Java, hạn chế quá tải tài nguyên và chống nghẽn kết nối. |
| **Trí Tuệ Nhân Tạo (Generative AI)** | **Google Gemini** | `gemini-3.6-flash` | Tốc độ suy luận tính bằng mili-giây, thông minh vượt trội, hỗ trợ sinh JSON có cấu trúc (`ResponseSchema`). |
| **Bảo Mật Mật Khẩu** | **jBCrypt** | 0.4.3 | Mã hóa mật khẩu một chiều với thuật toán Blowfish (Cost 12), chống tấn công vét cạn (Brute-force) và Rainbow table. |
| **Xử Lý JSON** | **Google Gson** | 2.10.1 | Chuyển đổi hai chiều POJO $\leftrightarrow$ JSON, cấu hình TypeAdapter cho `LocalDateTime` và định dạng UTF-8 chuẩn. |
| **Đóng Gói & Triển Khai (DevOps)** | **Docker (Multi-stage)** | Alpine Linux | Đóng gói tự động từ source code (`maven:3.9.6-alpine`) sang runtime image (`jetty:11-jre17-alpine`) siêu nhẹ (~180MB). |
| **Frontend UI / Styling** | **Vanilla JS, HTML5, CSS3, Bootstrap** | 5.3.3 | Không dùng framework nặng (React/Angular) để đảm bảo tốc độ tải trang cực nhanh (< 500ms), dễ bảo trì và mở rộng. |
| **Icons & Trực Quan Hóa** | **FontAwesome 6, SweetAlert2, Highlight.js, Marked.js, Canvas-Confetti** | Latest CDN | Hiển thị mã nguồn tô màu cú pháp chuẩn (One Dark), popup thông báo hiện đại và hiệu ứng pháo hoa củng cố thành tích. |

---

## ⚙️ 3. Nguyên Lý Hoạt Động Cốt Lõi (Core Operating Principles)

### 1. Nguyên Lý Gắn Nhãn Tự Tin (Confidence Tagging - ADR-009)
* **Vấn đề sư phạm:** Sinh viên làm trắc nghiệm thường có yếu tố may rủi (đoán mò). Nếu đoán bừa trúng đáp án đúng, các LMS truyền thống sẽ bỏ qua, khiến lỗ hổng kiến thức bị che giấu.
* **Giải pháp của hệ thống:**
  - Ở mỗi câu hỏi, sinh viên chủ động chọn: 🟢 **Chắc chắn (Certain)** hoặc 🟡 **Đoán mò (Guess)**.
  - Khi sinh viên chọn ĐÚNG nhưng với tâm thế **ĐOÁN MÒ**, thuật toán sẽ kích hoạt Gemini AI để tạo lời khuyên củng cố chuyên sâu, giải thích **tại sao đáp án đó đúng** và nguyên lý cốt lõi, giúp sinh viên thực sự làm chủ kiến thức thay vì dựa vào vận may.

### 2. Nguyên Lý Chẩn Đoán 4 Nhóm Lỗ Hổng Tư Duy (Pedagogical Misconceptions)
Hệ thống không chỉ chấm "Đúng/Sai" mà phân tích sâu bản chất nguyên nhân lỗi sai vào 4 nhóm nhận thức:
1. **`syntax_swap` (Nhầm lẫn cú pháp):** Nhầm lẫn toán tử (`==` vs `.equals()`), đặc tả ngôn ngữ, khai báo biến, thứ tự tham số.
2. **`boundary_blindness` (Mù điều kiện biên):** Bỏ quên trường hợp mảng rỗng, chỉ mục vượt quá giới hạn mảng (`IndexOutOfBounds`), kiểm tra giá trị `null` hoặc điều kiện dừng đệ quy.
3. **`mental_model_gap` (Lỗ hổng mô hình tư duy):** Hiểu sai cơ chế hướng đối tượng (OOP), đa hình, tham chiếu bộ nhớ Heap vs Stack, luồng vòng đời đối tượng.
4. **`logic_flaw` (Lỗi logic điều kiện):** Sai sót trong biểu thức Boolean phức tạp, phân nhánh `if-else` lồng nhau, điều kiện lặp vô tận.

### 3. Động Cơ Phục Hồi Kiến Thức Thích Ứng (Adaptive Remediation Engine)
* Sau khi nộp bài, mỗi câu làm sai hoặc đoán mò đều sinh ra một **Thẻ Bài Học Củng Cố Cá Nhân Hóa (AI Remedial Lesson)**.
* Sinh viên có thể nhấn nút **"Làm Bài Tập Phục Hồi"**: Hệ thống khởi tạo ngay một **Mini-Quiz 3 câu hỏi thích ứng** xoay quanh chính lỗ hổng vừa mắc phải.
* Khi hoàn thành tốt, hệ thống bắn hiệu ứng pháo hoa **Confetti** rực rỡ và cấp huy hiệu **"ĐÃ PHỤC HỒI KIẾN THỨC"**, đánh dấu sinh viên đã xóa bỏ thành công lỗ hổng tư duy đó.

### 4. Cơ Chế Bộ Đệm Dự Phòng Ngoại Tuyến (Dual Fallback & Smart FAQ Buffer - ADR-008)
* Hệ thống ứng dụng mô hình thiết kế **Circuit Breaker**:
  - `Ưu tiên 1`: Gọi **Gemini 3.6 Flash** với Structured JSON Schema.
  - `Ưu tiên 2 (Tự động chuyển tiếp)`: Nếu model 3.6 quá tải, hệ thống tự chuyển tiếp sang **Gemini 3.8 Flash**.
  - `Ưu tiên 3 (Cứu sinh ngoại tuyến)`: Nếu không có Internet hoặc hết hạn mức API, `FallbackService` tự động trích xuất các phân tích có sẵn trong CSDL và trả về ngay lập tức. **Người dùng không bao giờ gặp màn hình trắng hoặc lỗi 500!**

### 5. Studio Soạn Đề Tự Động & Trợ Lý Co-Pilot Dành Cho Giảng Viên (Teacher AI Co-Pilot)
* Giảng viên có toàn quyền:
  - Chọn chủ đề, mức độ khó (Dễ / Trung bình / Khó) và nhóm lỗ hổng tư duy mục tiêu.
  - Gemini AI tự động sinh bộ câu hỏi trắc nghiệm chuẩn sư phạm theo **Thang đo nhận thức Bloom (Bloom's Taxonomy)**.
  - Xem trước định dạng code chuẩn xác, tùy biến nội dung và **Import 1-Click** vào ngân hàng đề CSDL PostgreSQL.
  - Khung chat **AI Co-Pilot Sư Phạm** hỗ trợ giảng viên thiết kế kế hoạch bài giảng và ra đề thi phân hóa.

---

## 🔄 4. Sự Liên Kết & Luồng Dữ Liệu Thực Tế (Data Flow Walkthrough)

### 📌 Luồng 1: Sinh Viên Thi & Nhận Bài Học Thích Ứng
1. **Làm bài (`quiz.html`):** Sinh viên chọn đáp án + gắn thẻ độ tự tin (`CERTAIN` / `GUESS`). Tiến độ được `quiz.js` tự động lưu trữ phòng sự cố mất điện/reload (`Autosave LocalStorage`).
2. **Nộp bài (`POST /api/quiz/submit`):**
   - `QuizServlet` tiếp nhận danh sách đáp án, xác thực session người dùng.
   - `QuizService` lưu các câu trả lời vào bảng `user_answers` và cập nhật điểm số vào `quiz_sessions`.
   - `QuizService` lọc các câu sai hoặc đoán mò $\rightarrow$ chuyển qua `PromptBuilder` để đóng gói ngữ cảnh sư phạm $\rightarrow$ gửi đến `AIService`.
   - `AIService` gọi Gemini API bằng phương thức `generateContent` với schema JSON ép kiểu nghiêm ngặt.
   - Kết quả trả về được lưu trữ vào bảng `remedial_lessons`.
3. **Phân tích kết quả (`result.html`):** Hiển thị trực quan bảng điểm, danh sách bài học củng cố, phân tích nhận thức và nút kích hoạt Mini-Quiz phục hồi kiến thức.

### 📌 Luồng 2: Giảng Viên Soạn Đề Bằng AI Studio
1. Giảng viên mở `teacher-dashboard.html`, chọn Tab **"Trợ Lý AI Soạn Đề & Bài Tập"**.
2. Thiết lập cấu hình yêu cầu $\rightarrow$ gửi `POST /api/teacher/ai/generate`.
3. `TeacherServlet` kiểm tra phân quyền `TEACHER`/`ADMIN` $\rightarrow$ chuyển sang `AIService.generateQuestionsForTeacher(...)`.
4. Gemini AI sinh danh sách câu hỏi cấu trúc JSON gồm: nội dung Markdown, các phương án A/B/C/D, đáp án đúng, giải thích sư phạm, thẻ lỗ hổng tư duy mục tiêu.
5. Giao diện hiển thị danh sách câu hỏi trực quan $\rightarrow$ Giảng viên nhấn **"Lưu Vào Ngân Hàng Đề"** $\rightarrow$ gọi `QuestionDAO.createQuestion()` ghi thẳng vào CSDL PostgreSQL.

### 📌 Luồng 3: Cộng Đồng Học Tập & Thảo Luận Môn Học
1. Người học mở `community.html`, nhấn nút **"Đăng bài"** $\rightarrow$ mở modal soạn thảo học thuật (Facebook Style Composer).
2. Người học có thể chèn khối Code lập trình, Mẹo né bẫy nhận thức, Thử thách câu hỏi mini, hoặc Trích dẫn giáo trình, kèm chọn trạng thái học tập.
3. Người học chọn hoặc gõ tên môn học bất kỳ (Java OOP, CSDL, Toán rời rạc, AI...) $\rightarrow$ gửi `POST /api/community/posts`.
4. `CommunityServlet` tiếp nhận: Nếu môn học chưa có, tự động tạo mới vào bảng `topics`; lưu bài viết vào `community_posts` và phát hành lên bảng tin cộng đồng.

---

## 📐 5. Thiết Kế Hệ Thống Chi Tiết: Biểu Đồ Tuần Tự & Biểu Đồ Cộng Tác (Sequence & Collaboration Diagrams)

> Chi tiết phân tích đầy đủ và mã nguồn PlantUML tham khảo tại: [`SYSTEM_DIAGRAMS.md`](SYSTEM_DIAGRAMS.md).

### 5.1. Biểu Đồ Tuần Tự — Tạo Bài Thảo Luận Học Tập (Sequence Diagram)
*(Thiết kế theo mô hình phân tích chuẩn BCE: Actor $\rightarrow$ Boundary $\rightarrow$ Control $\rightarrow$ Entity $\rightarrow$ Database)*

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng
    participant UI as 🖥️ Giao diện thảo luận (Boundary)
    participant Ctrl as ⚙️ CommunityServlet (Control)
    participant TopicEnt as 📚 Topic / Môn học (Entity)
    participant PostEnt as 📝 CommunityPost (Entity)
    participant DB as 🗄️ CSDL PostgreSQL

    User ->> UI: 1. Yêu cầu tạo bài thảo luận
    activate UI
    UI -->> User: 1.1. Hiển thị modal soạn thảo học thuật (Facebook Style)
    deactivate UI

    User ->> UI: 2. Nhập thông tin & bấm "Đăng bài" (tiêu đề, nội dung, kênh, tên môn học)
    activate UI
    UI ->> Ctrl: 3. Gửi yêu cầu tạo bài viết (POST /api/community/posts)
    activate Ctrl

    alt Dữ liệu không hợp lệ (Tiêu đề hoặc nội dung trống)
        Ctrl -->> UI: 4. Trả lỗi HTTP 400 (Yêu cầu điền đủ tiêu đề/nội dung)
        UI -->> User: 4.1. Hiển thị cảnh báo lỗi
    else Dữ liệu hợp lệ
        Ctrl ->> TopicEnt: 5. Kiểm tra môn học (topicId, topicName)
        activate TopicEnt
        TopicEnt ->> DB: 5.1. Truy vấn môn học theo tên/mã
        activate DB
        DB -->> TopicEnt: 5.2. Trả kết quả truy vấn
        deactivate DB

        alt Môn học đã có sẵn trong CSDL
            TopicEnt -->> Ctrl: 6. Trả về topicId hiện có
        else Môn học mới (Người dùng tự nhập môn mới)
            TopicEnt ->> DB: 6.1. Thêm mới môn học vào bảng topics (INSERT)
            activate DB
            DB -->> TopicEnt: 6.2. Trả về mã môn học mới vừa tạo
            deactivate DB
            TopicEnt -->> Ctrl: 6.3. Trả về topicId mới
        end
        deactivate TopicEnt

        Ctrl ->> PostEnt: 7. Lưu bài thảo luận (userId, topicId, channel, title, content)
        activate PostEnt
        PostEnt ->> DB: 7.1. Ghi nhận bài thảo luận vào bảng community_posts (INSERT)
        activate DB
        DB -->> PostEnt: 8. Lưu thành công (trả về postId mới)
        deactivate DB
        PostEnt -->> Ctrl: 8.1. Thông báo tạo bài thành công
        deactivate PostEnt
        Ctrl -->> UI: 9. Trả kết quả thành công (HTTP 200)
        deactivate Ctrl
        UI -->> User: 10. Đóng modal, hiển thị thông báo thành công & cập nhật bảng tin
    end
    deactivate UI
```

### 5.2. Biểu Đồ Cộng Tác — Tạo Bài Thảo Luận Học Tập (Collaboration Diagram)
*(Bố cục 4 đỉnh đối tượng với các thông điệp có hướng và số thứ tự phân cấp y hệt Form Hình 2)*

```mermaid
flowchart TD
    subgraph TopRow [" "]
        direction LR
        BN[":Người dùng<br/>(Sinh viên/Giảng viên)"]
        GD[":Giao diện thảo luận<br/>(community.html)"]
    end

    subgraph BottomRow [" "]
        direction LR
        CSDL[":Cơ sở dữ liệu<br/>(PostgreSQL)"]
        HTDK[":Hệ thống điều khiển<br/>(CommunityServlet & DAOs)"]
    end

    %% Cạnh ngang trên
    BN ---|"1: Yêu cầu mở form tạo bài ➔<br/>◀ 1.1: Hiển thị form soạn thảo<br/>1.2: Nhập thông tin & bấm Đăng bài ➔<br/>◀ 1.3: Thông báo kết quả đăng bài"| GD

    %% Cạnh dọc phải
    GD ---|"1.2: Gửi thông tin bài viết ➔<br/>◀ 1.2.1: Tiếp nhận và xác thực<br/>◀ 1.2.3: Trả kết quả xử lý thành công"| HTDK

    %% Cạnh ngang dưới
    HTDK ---|"1.2.1.1: Kiểm tra / Khởi tạo môn học ➔<br/>1.2.1.2: Lưu bài thảo luận vào CSDL ➔<br/>◀ 1.2.2: Trả dữ liệu xác nhận ghi CSDL"| CSDL
```

---

## 🚀 6. Hướng Dẫn Cài Đặt & Khởi Chạy

### Cách 1: Khởi Chạy Siêu Tốc Bằng Docker (Khuyên Dùng)

```bash
# 1. Đóng gói Docker container
docker build -t lms-ai:latest .

# 2. Khởi chạy container gắn kèm biến môi trường
docker run -d -p 8080:8080 --env-file .env --name lms-app lms-ai:latest

# 3. Mở trình duyệt truy cập:
# http://localhost:8080/auth.html
```

---

### Cách 2: Chạy Trực Tiếp Bằng Maven Wrapper (Local)

#### Bước 1: Chuẩn Bị Môi Trường
- **JDK:** Phiên bản **Java 17 LTS hoặc Java 21 LTS** (Khuyên dùng Java 17).
- Không cần cài sẵn Maven (dự án đã có sẵn `mvnw.cmd`).

#### Bước 2: Cấu Hình Biến Môi Trường (`.env`)
Tạo file `.env` từ `.env.example` với các thông số kết nối CSDL Neon.tech PostgreSQL:
```ini
# CSDL PostgreSQL Cloud (Neon.tech / Render)
DB_URL=jdbc:postgresql://ep-mute-queen-b3xtkksd-pooler.c-4.ap-southeast-1.aws.neon.tech/lms_db?sslmode=require
DB_USERNAME=lms_db_owner
DB_PASSWORD=npg_r4vyIfJa9tSX
DB_POOL_SIZE=10

# Khóa Google Gemini API (Model thế hệ mới)
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-3.6-flash

# Cổng lắng nghe
PORT=8080
```

> 💡 **TÍNH NĂNG AUTO-MIGRATION & AUTO-SEED:**  
> Lớp `DatabaseUtil` tích hợp cơ chế tự động dò tìm cấu trúc bảng. Nếu CSDL trống, hệ thống sẽ **tự động chạy `schema.sql`** và nạp 10 câu hỏi mẫu chất lượng cao mà bạn không cần phải cấu hình thủ công!

#### Bước 3: Khởi Động Máy Chủ
```powershell
# Trên Windows:
.\mvnw.cmd jetty:run

# Trên Linux / macOS:
./mvnw jetty:run
```

Truy cập: **`http://localhost:8080/auth.html`**

### Cách 3: Triển Khai Cloud & Cơ Chế Hoạt Động 24/7 (Zero Cold-Start)

Hệ thống đã được đóng gói và kiểm thử triển khai trên nền tảng đám mây:
1. **Cloud Web Service (Render):** Chạy trực tiếp từ `Dockerfile` liên kết tự động với repository GitHub.
2. **Serverless Database (Neon.tech PostgreSQL):** Lưu trữ CSDL quan hệ trên đám mây với tính năng Auto-Migration.
3. **Cơ Chế Giữ Thức 24/7 (UptimeRobot Keep-Alive):**
   - Render bản Free sẽ tự động ngủ sau 15 phút idle (gây trễ cold-start 30-50s).
   - Thiết lập monitor trên [UptimeRobot.com](https://uptimerobot.com) ping định kỳ **10 phút/lần** vào endpoint `https://cnpm-ck-intelligent-learning-system.onrender.com/auth.html`.
   - Kết quả: Web luôn thức 24/7, phản hồi dưới 1 giây mà vẫn nằm trọn trong hạn mức miễn phí 744/750 giờ/tháng của Render!

---

## 🔑 7. Danh Sách Tài Khoản Thử Nghiệm

| Vai Trò | Tên Đăng Nhập | Mật Khẩu | Điểm Đến | Đặc Quyền & Tính Năng Nổi Bật |
|:---:|:---:|:---:|:---:|---|
| 👩‍🏫 **Giảng Viên** | `giangvien01` | `demo123` | **Teacher Dashboard**<br>(`teacher-dashboard.html`) | • 4 Thẻ KPI thời gian thực.<br>• Quản lý ngân hàng câu hỏi (Thêm/Sửa/Xóa, Tìm kiếm realtime, Phân trang).<br>• AI Pedagogical Insight (Thống kê 4 nhóm lỗi sai).<br>• Studio Trợ Lý AI Soạn Đề & Khung Chat Co-Pilot. |
| 👨‍🎓 **Sinh Viên** | `sinhvien01` | `demo123` | **Trang Chủ Sinh Viên**<br>(`index.html`) | • Làm bài thi trắc nghiệm + Confidence Tagging.<br>• Tự động lưu bài dở dang (Autosave).<br>• Phân tích kết quả + Bài tập thích ứng (Mini-Quiz).<br>• Trò chuyện với Trợ Giảng AI 3 nhân cách.<br>• Diễn đàn học tập Facebook-style & né bẫy tư duy (`community.html`). |
| 🛡️ **Quản Trị Viên** | `admin` | `demo123` | **Teacher Dashboard** | Toàn quyền kiểm soát hệ thống và dữ liệu. |

---

## 📂 8. Cấu Trúc Mã Nguồn Dự Án (Project Structure)

```
Hệ thống học tập thông minh/
├── Dockerfile                         # Multi-stage Docker packaging (Maven Build -> Jetty 11 Runtime)
├── .dockerignore                      # Loại trừ các file rác khi build image
├── pom.xml                            # Quản lý dependency (Jetty 11, PostgreSQL, Gson, BCrypt...)
├── export_codebase.ps1                # Script tự động xuất toàn bộ mã nguồn ra FULL_CODEBASE.md
├── FULL_CODEBASE.md                   # File tổng hợp toàn bộ 58 file mã nguồn của dự án
├── PROJECT_STATE.md                   # Sổ tay ghi chép tiến độ kỹ thuật giữa các phiên
├── SYSTEM_DIAGRAMS.md                 # Tài liệu Biểu đồ tuần tự (Sequence) & Cộng tác (Collaboration)
├── REFACTOR_REPORT.md                 # Báo cáo kiểm toán bảo mật & khả năng phục hồi (Audit)
├── README.md                          # Tài liệu kiến trúc và hướng dẫn vận hành toàn diện
├── src/
│   └── main/
│       ├── java/com/lms/
│       │   ├── model/                 # POJO Models (User, Topic, Question, QuizSession, CommunityPost...)
│       │   ├── dao/                   # Data Access Objects (DatabaseUtil, UserDAO, QuizDAO, CommunityDAO...)
│       │   ├── service/               # Nghiệp vụ lõi (AIService, QuizService, UserService, PromptBuilder...)
│       │   ├── servlet/               # REST Controllers (AuthServlet, QuizServlet, CommunityServlet...)
│       │   ├── filter/                # Bộ lọc an ninh & phân quyền (CorsFilter, AuthFilter)
│       │   └── util/                  # Tiện ích bổ trợ (ConfigLoader, JsonHelper)
│       ├── resources/
│       │   └── db/schema.sql          # Kịch bản CSDL PostgreSQL (DDL, Indexes & Initial Data)
│       └── webapp/
│           ├── css/app.css            # Hệ thống CSS Design Tokens, Glassmorphism & Animations
│           ├── js/                    # Mã JavaScript hướng module
│           │   ├── api.js             # Transport layer: timeout, abort, retry, offline detection
│           │   ├── ui.js              # Presentation utility: safe markdown, sanitize HTML chống XSS
│           │   ├── community.js       # Diễn đàn học tập Facebook Style: Code, Bẫy tư duy, Quiz mini
│           │   ├── quiz.js            # Logic làm bài thi & Autosave
│           │   ├── result.js          # Logic kết quả & Adaptive Mini-Quiz
│           │   ├── teacher.js         # Logic Teacher Dashboard & Studio AI
│           │   └── chat-widget.js     # Trợ giảng AI đa nhân cách
│           ├── auth.html              # Màn hình Đăng nhập & Đăng ký (Toggle Password Eyes)
│           ├── community.html         # Diễn đàn thảo luận & cộng đồng học tập đa kênh
│           ├── index.html             # Cổng thông tin môn học & chọn chủ đề ôn luyện
│           ├── quiz.html              # Màn hình thi trắc nghiệm & Gắn nhãn tự tin
│           ├── result.html            # Báo cáo kết quả & Bài tập phục hồi thích ứng
│           ├── teacher-dashboard.html # Bảng điều khiển giảng viên & AI Soạn đề Studio
│           └── history.html           # Lịch sử làm bài & theo dõi tiến bộ học tập
```

---

## 👥 9. Thông Tin Đồ Án
- **Môn học:** Công Nghệ Phần Mềm (CNPM) — Học kỳ Cuối
- **Phiên bản:** 1.0-SNAPSHOT (Production Cloud Deployed on Render)
- **Bản quyền:** Đồ Án Nhóm Phát Triển LMS Thông Minh 2026.

---

## 🛡️ 10. Gói Cải Tiến Bảo Mật & Ổn Định (Refactor 2026-09-22)
- **API Transport Resilience:** `js/api.js` bổ sung timeout (10s), AbortController, bắt lỗi offline, `ApiError` có phân loại và retry có kiểm soát đối với phương thức `GET`.
- **Phòng chống IDOR (Object-Level Authorization):** `QuizServlet` và `QuizDAO` siết chặt quyền sở hữu, ngăn chặn sinh viên xem kết quả session của tài khoản khác.
- **Tính toàn vẹn bài thi (Submission Integrity):** Ngăn chặn gửi trùng câu hỏi, câu hỏi lệch topic, nộp lại session đã hoàn tất và thiếu câu trả lời.
- **Bảo mật Frontend & XSS Sanitization:** `js/ui.js` chuẩn hóa lọc và vô hiệu hóa các thẻ script, sự kiện độc hại trong cú pháp Markdown do người dùng hoặc AI sinh ra trước khi render vào DOM.
- **CORS Allowlist:** Loại bỏ wildcard reflection khi có credentials, cho phép chỉ định rõ danh sách domain được phép qua biến `CORS_ALLOWED_ORIGINS`.
- **Quyền tự do ngôn luận & Ẩn bài viết:** Diễn đàn cộng đồng chỉ cho phép chính chủ tác giả xóa bài viết; người dùng khác được cấp tính năng Ẩn bài viết khỏi bảng tin cá nhân (có thể phục hồi bất kỳ lúc nào).
