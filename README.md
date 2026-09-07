# 🎓 Hệ Thống Học Tập Thông Minh Tích Hợp AI (Intelligent LMS)

> **Đồ Án Cuối Kỳ — Môn Công Nghệ Phần Mềm**  
> Nền tảng LMS hiện đại giúp phát hiện quan niệm sai lầm (**Pedagogical Misconception Detection**), kích hoạt tính năng **Confidence Tagging**, tự động kiến tạo **Bài học củng cố cá nhân hóa (AI Remedial Lessons)** và bài tập hồi quy thích ứng (**Adaptive Remediation Engine**) bằng **Google Gemini 2.5 Flash**.

---

## 🌟 Tính Năng Nổi Bật

1. **Kiểm Tra Trắc Nghiệm Thông Minh & Gắn Nhãn Tự Tin (Confidence Tagging - ADR-009):**
   - Sinh viên khi làm bài có thể đánh dấu: 🟢 **Chắc chắn (Certain)** hoặc 🟡 **Đoán mò (Guess)**.
   - Nếu chọn ĐÚNG nhưng với tâm thế **ĐOÁN MÒ**, hệ thống AI vẫn can thiệp để củng cố bản chất lý thuyết, biến kiến thức may rủi thành nền tảng vững vàng.
   - Tự động lưu tiến độ làm bài (**Autosave**) vào `localStorage` và hỏi khôi phục bài làm dở dang khi reload trang.
   - Màn hình chờ AI Loading Overlay toàn màn hình với chu kỳ thông điệp sư phạm sinh động.

2. **Chẩn Đoán & Phân Loại Lỗ Hổng Tư Duy (Misconception Diagnosis):**
   - Tự động phân loại lỗi sai vào 4 nhóm nhận thức cốt lõi:
     - `syntax_swap`: Nhầm lẫn cú pháp toán tử, đặc tả ngôn ngữ.
     - `boundary_blindness`: Bỏ quên điều kiện biên, kiểm tra ranh giới mảng, null pointer.
     - `mental_model_gap`: Lỗ hổng mô hình tư duy (OOP, luồng quản lý bộ nhớ Heap/Stack).
     - `logic_flaw`: Lỗi phân nhánh logic điều kiện if/else, vòng lặp.

3. **Công Cụ Phục Hồi Thích Ứng (Adaptive Remediation Engine):**
   - Cung cấp thẻ chẩn đoán lỗi nhận thức kèm nút **Làm Bài Tập Phục Hồi (Mini-Quiz 3 câu)** tập trung đúng dạng sai sót.
   - Hiệu ứng pháo hoa **Confetti** rực rỡ và huy hiệu **ĐÃ PHỤC HỒI KIẾN THỨC** khi sinh viên làm chủ được lỗ hổng tư duy.

4. **Bảng Điều Khiển Giảng Viên Hiện Đại (Teacher Dashboard):**
   - 4 Thẻ KPI thời gian thực: Sinh viên làm bài, Ngân hàng câu hỏi, Điểm trung bình, Tỷ lệ đoán mò (GUESS rate).
   - Quản lý ngân hàng câu hỏi (CRUD câu hỏi, soạn thảo mã nguồn Markdown, tìm kiếm realtime, phân trang 10 câu/trang).
   - Tab AI Pedagogical Insight thống kê tần suất các dạng lỗi tư duy sinh viên hay mắc phải nhất.

5. **Bộ Đệm Dự Phòng Ngoại Tuyến (AI Fallback Buffer - ADR-008):**
   - Nếu mất mạng, hết quota hoặc chưa cấu hình API Key, hệ thống **không bao giờ bị lỗi** mà tự động kích hoạt cơ chế Fallback nội bộ từ kho giải thích chuẩn trong CSDL.

6. **Trợ Giảng AI Đa Nhân Cách (Multi-Persona Chatbot Widget - ADR-011):**
   - Widget chat nổi góc màn hình 24/7 với 3 persona:
     - 🎓 **Bạn Học Kèm (Peer Tutor):** Thân thiện, mẹo nhớ nhanh.
     - 💻 **Senior Developer:** Thực chiến, chuẩn dự án doanh nghiệp.
     - 🏛️ **Giáo Sư (Professor):** Học thuật, phân tích nguồn gốc lý thuyết.

---

## 🛠️ Yêu Cầu Môi Trường (Prerequisites)

Trước khi cài đặt, hãy đảm bảo máy tính của bạn đã cài đặt các phần mềm sau:

1. **Java Development Kit (JDK):**
   - Phiên bản: **JDK 17, JDK 21 trở lên** (Khuyên dùng Java 21 LTS hoặc Java 25).
   - Kiểm tra bằng lệnh:
     ```powershell
     java -version
     ```
2. **Hệ Quản Trị Cơ Sở Dữ Liệu:**
   - **Microsoft SQL Server** (bản 2017, 2019, 2022 hoặc Developer / Express).
   - Công cụ quản lý: **SQL Server Management Studio (SSMS)** hoặc **Azure Data Studio**.
3. **Không cần cài đặt sẵn Maven:**
   - Dự án đã tích hợp sẵn **Maven Wrapper** (`mvnw`, `mvnw.cmd`, `mvnw.ps1`), tự động tải đúng phiên bản Maven khi chạy lần đầu.
4. **Git:** Đã cài Git để clone repository.

---

## 🚀 Hướng Dẫn Cài Đặt & Khởi Chạy Local (Từng Bước)

### Bước 1: Clone Repository Về Máy

Mở Terminal / PowerShell trên máy tính của bạn và thực hiện lệnh:

```bash
git clone https://github.com/24110257ak/CNPM-CK--Intelligent-learning-system.git
cd "CNPM-CK--Intelligent-learning-system/Hệ thống học tập thông minh"
```

---

### Bước 2: Khởi Tạo Cơ Sở Dữ Liệu SQL Server

1. Mở **SQL Server Management Studio (SSMS)** và đăng nhập vào SQL Server local của bạn (bằng tài khoản `sa` hoặc Windows Authentication).
2. Nhấn `Ctrl + O` (hoặc vào File -> Open -> File) và mở file script tại đường dẫn:
   ```
   src/main/resources/db/schema.sql
   ```
3. Nhấn **Execute (F5)** để thực thi script. Script này sẽ tự động:
   - Tạo cơ sở dữ liệu `lms_db` (nếu chưa có).
   - Tạo 7 bảng dữ liệu quan hệ chuẩn 3NF: `users`, `topics`, `questions`, `quiz_sessions`, `user_answers`, `remedial_lessons`, `chat_messages`.
   - Nạp sẵn các chủ đề học tập, ngân hàng câu hỏi lập trình có định dạng Markdown code và các tài khoản thử nghiệm.
4. *(Tùy chọn nâng cao)*: Ứng dụng có cơ chế **Auto-Migration** (`DatabaseUtil.java`) tự động kiểm tra và thêm cột `misconception_tag` cũng như tối ưu hóa ràng buộc CSDL ngay khi khởi chạy máy chủ, người dùng không cần can thiệp thủ công.

---

### Bước 3: Cấu Hình Biến Môi Trường (`.env`)

Tại thư mục `Hệ thống học tập thông minh`, tạo file `.env` bằng cách copy từ file mẫu `.env.example`:

- **Trên Windows PowerShell:**
  ```powershell
  Copy-Item .env.example .env
  ```
- **Trên Windows Command Prompt (CMD):**
  ```cmd
  copy .env.example .env
  ```
- **Trên Linux / macOS:**
  ```bash
  cp .env.example .env
  ```

Mở file `.env` vừa tạo bằng VS Code hoặc Notepad và điều chỉnh mật khẩu SQL Server của bạn:

```ini
# ── CSDL Microsoft SQL Server Local ──
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=lms_db;trustServerCertificate=true;encrypt=true;
DB_USERNAME=sa
DB_PASSWORD=Điền_mật_khẩu_SQL_Server_của_bạn_ở_đây
DB_POOL_SIZE=10

# ── Google Gemini API (Tùy chọn) ──
# Lấy API Key miễn phí tại: https://aistudio.google.com
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-2.5-flash

# ── Cổng chạy máy chủ Web (Jetty Dev Server) ──
PORT=8080
```

> 💡 **MẸO QUAN TRỌNG VỀ GEMINI API KEY:**  
> Nếu bạn chưa có API Key hoặc chưa muốn cấu hình ngay, hãy giữ nguyên giá trị mặc định. Nhờ kiến trúc **ADR-008 (AI Fallback Buffer)**, hệ thống sẽ tự động dùng bộ đệm ngoại tuyến thông minh từ CSDL để tạo bài học củng cố cho sinh viên mà **không hề bị gián đoạn hay báo lỗi**!

---

### Bước 4: Khởi Động Máy Chủ Web (Jetty 11)

Tại thư mục `Hệ thống học tập thông minh`, chạy lệnh sau để khởi động:

- **Trên Windows (PowerShell hoặc CMD):**
  ```powershell
  .\mvnw.cmd jetty:run
  ```
  *(Nếu sử dụng PowerShell gặp chính sách script, bạn có thể chạy: `.\mvnw.ps1 jetty:run`)*

- **Trên Linux / macOS:**
  ```bash
  ./mvnw jetty:run
  ```

Lần chạy đầu tiên, Maven Wrapper sẽ tự động tải các dependencies cần thiết (khoảng 30 giây - 1 phút). Khi màn hình xuất hiện thông báo:

```
[DatabaseUtil] ✅ Auto-Migration: Cột [misconception_tag] đã sẵn sàng!
[INFO] Started Server@...
```

👉 Nghĩa là máy chủ đã khởi động thành công và đang lắng nghe tại cổng `8080`!

---

### Bước 5: Mở Trình Duyệt & Trải Nghiệm

Mở trình duyệt (Chrome, Edge, Firefox) và truy cập vào địa chỉ:

👉 **`http://localhost:8080/auth.html`** (hoặc **`http://localhost:8080/`**)

---

## 🔑 Danh Sách Tài Khoản Thử Nghiệm Có Sẵn

Hệ thống đã chuẩn bị sẵn các tài khoản demo sau khi bạn chạy `schema.sql`:

| Vai Trò | Tên Đăng Nhập | Mật Khẩu | Điểm Đến Sau Đăng Nhập | Chức Năng Nổi Bật Để Trải Nghiệm |
|:---:|:---:|:---:|:---:|---|
| 👩‍🏫 **Giảng Viên** | `giangvien01`<br>*(hoặc `giangvien02`)* | `demo123` | **Teacher Dashboard**<br>(`teacher-dashboard.html`) | • Xem 4 thẻ KPI tổng quan hệ thống.<br>• Quản lý ngân hàng câu hỏi (thêm, sửa, xóa, tìm kiếm realtime, phân trang 10 câu/trang).<br>• Xem phân tích chẩn đoán sai sót AI Pedagogical Insight. |
| 👨‍🎓 **Sinh Viên** | `sinhvien01`<br>*(hoặc `sinhvien02`)* | `demo123` | **Trang Chủ Sinh Viên**<br>(`index.html`) | • Làm bài thi trắc nghiệm kèm bộ chọn **Confidence Tagging** (Chắc chắn vs Đoán mò).<br>• Tự động lưu tiến độ & khôi phục bài làm dở dang.<br>• Màn hình chờ AI Loading Overlay phát sáng.<br>• Trang kết quả: Làm bài tập thích ứng (Mini-Quiz 3 câu) -> Bắn pháo hoa Confetti và nhận huy hiệu "ĐÃ PHỤC HỒI KIẾN THỨC".<br>• Trò chuyện với Chatbot Trợ Giảng AI 3 nhân cách. |
| 🛡️ **Quản Trị Viên** | `admin` | `demo123` | **Teacher Dashboard** | Có đầy đủ mọi đặc quyền của Giảng viên và Quản trị viên. |

> Bạn cũng có thể bấm sang tab **Đăng Ký** trên trang `auth.html` để tạo tài khoản sinh viên mới kèm theo sở thích cá nhân hóa (ví dụ: đá bóng, chơi game, anime) để AI cá nhân hóa bài giảng!

---

## ❓ Xử Lý Sự Cố Thường Gặp (Troubleshooting / FAQ)

### 1. Lỗi kết nối CSDL: `Cannot get a connection from the pool` hoặc `Login failed for user 'sa'`
- **Nguyên nhân:** Mật khẩu `DB_PASSWORD` trong file `.env` chưa đúng với mật khẩu SQL Server trên máy bạn, hoặc dịch vụ SQL Server chưa bật.
- **Cách khắc phục:**
  1. Mở `services.msc` trên Windows, tìm `SQL Server (MSSQLSERVER)` hoặc `SQL Server (SQLEXPRESS)` và đảm bảo trạng thái là **Running**.
  2. Mở SSMS, chuột phải vào tên Server -> chọn **Properties** -> mục **Security** -> chọn **SQL Server and Windows Authentication mode**.
  3. Mở **SQL Server Configuration Manager** -> mục **SQL Server Network Configuration** -> **Protocols for MSSQLSERVER** -> đảm bảo **TCP/IP** đang ở trạng thái **Enabled** (cổng mặc định 1433).
  4. Cập nhật lại chính xác `DB_PASSWORD` trong file `.env`.

### 2. Lỗi cổng bị trùng: `Address already in use: bind`
- **Nguyên nhân:** Cổng 8080 đang bị một ứng dụng khác chiếm dụng (như Tomcat, Oracle XE, hoặc tiến trình Jetty cũ chưa tắt).
- **Cách khắc phục:**
  - Mở file `.env` và đổi port sang cổng khác: `PORT=8081` hoặc `PORT=8888`.
  - Hoặc trên Windows, tìm và tắt process đang giữ cổng 8080:
    ```powershell
    Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force
    ```

### 3. Lỗi: `'mvnw.cmd' is not recognized` hoặc lỗi `JAVA_HOME is not set`
- **Nguyên nhân:** Máy tính chưa cài đặt JDK hoặc chưa cấu hình biến môi trường `JAVA_HOME`.
- **Cách khắc phục:**
  1. Tải và cài đặt **JDK 21 LTS** từ [Oracle](https://www.oracle.com/java/technologies/downloads/) hoặc [Adoptium Temurin](https://adoptium.net/).
  2. Thiết lập biến môi trường `JAVA_HOME` trỏ tới thư mục cài đặt JDK (ví dụ: `C:\Program Files\Java\jdk-21`).
  3. Thêm `%JAVA_HOME%\bin` vào biến môi trường `Path`.

### 4. Hệ thống báo lỗi khóa file trên Windows khi chỉnh sửa code
- Dự án đã cấu hình sẵn thuộc tính `useFileMappedBuffer = false` cho `DefaultServlet` trong `web.xml`, giúp bạn có thể chỉnh sửa file HTML/CSS/JS thoải mái mà không lo bị Windows lock file trong lúc máy chủ đang chạy.

---

## 📂 Cấu Trúc Mã Nguồn Dự Án

```
Hệ thống học tập thông minh/
├── .mvn/wrapper/                  # Maven Wrapper tự động tải Maven tương thích
├── mvnw & mvnw.cmd & mvnw.ps1     # Script khởi chạy Maven (tối ưu hóa tiếng Việt)
├── pom.xml                        # Cấu hình Jetty 11, MSSQL JDBC, Gemini SDK
├── .env.example                   # Mẫu cấu hình môi trường chuẩn
├── src/
│   └── main/
│       ├── java/com/lms/
│       │   ├── model/             # POJO (User, Topic, Question, QuizSession, UserAnswer, RemedialLesson)
│       │   ├── dao/               # JDBC Data Access (DatabaseUtil HikariCP, UserDAO, QuestionDAO, QuizDAO...)
│       │   ├── service/           # AIService (Gemini), FallbackService, QuizService, PromptBuilder...
│       │   ├── servlet/           # AuthServlet, QuizServlet, QuestionServlet, TeacherServlet, TopicServlet...
│       │   ├── filter/            # CorsFilter, AuthFilter
│       │   └── util/              # ConfigLoader, JsonHelper
│       ├── resources/
│       │   └── db/schema.sql      # Kịch bản CSDL SQL Server 3NF tiếng Việt & dữ liệu mẫu
│       └── webapp/
│           ├── css/app.css        # Toàn bộ CSS giao diện Dark/Light mode & Animation
│           ├── js/
│           │   ├── api.js         # REST API Client & Toast SweetAlert2
│           │   ├── quiz.js        # Logic làm bài, Autosave, AI Loading Overlay
│           │   ├── result.js      # Logic kết quả, Mini-Quiz Adaptive Remediation, Confetti
│           │   ├── teacher.js     # Logic Teacher Dashboard (KPIs, CRUD câu hỏi, Tìm kiếm, Phân trang)
│           │   └── chat-widget.js # Logic Trợ giảng AI 3 nhân cách
│           ├── index.html         # Trang danh mục chủ đề & điều hướng sinh viên
│           ├── auth.html          # Đăng nhập & Đăng ký tài khoản
│           ├── quiz.html          # Giao diện làm bài thi + Confidence Tagging
│           ├── result.html        # Trang phân tích kết quả thi & Bài học phục hồi thích ứng
│           ├── teacher-dashboard.html # Bảng điều khiển dành cho Giảng viên
│           └── history.html       # Lịch sử bài làm & thống kê cá nhân
└── README.md
```

---

## 👥 Tác Giả & Bản Quyền
- **Đồ Án Cuối Kỳ:** Môn Công Nghệ Phần Mềm (CNPM - CK)
- **Công Nghệ:** Java 21 • Eclipse Jetty 11 • MS SQL Server • Google Gemini API
