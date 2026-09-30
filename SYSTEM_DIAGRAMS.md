# 📐 TÀI LIỆU THIẾT KẾ HỆ THỐNG: BIỂU ĐỒ TUẦN TỰ & BIỂU ĐỒ CỘNG TÁC
> **Dự án:** Hệ Thống Học Tập Thông Minh Tích Hợp AI (Intelligent LMS)  
> **Học phần:** Công Nghệ Phần Mềm Cuối Kỳ (CNPM - CK)  
> **Quy chuẩn thiết kế:** Mô hình phân tích BCE (Boundary - Control - Entity) & UML 2.5 chuẩn hóa.

---

## 📑 MỤC LỤC
1. [Nghiệp Vụ 1: Tạo Bài Viết Thảo Luận Học Tập & Tự Động Định Danh Môn Học](#1-nghiệp-vụ-1-tạo-bài-viết-thảo-luận-học-tập--tự-động-định-danh-môn-học)
   - [1.1. Biểu đồ tuần tự (Sequence Diagram)](#11-biểu-đồ-tuần-tự---tạo-bài-viết-thảo-luận)
   - [1.2. Biểu đồ cộng tác (Collaboration Diagram)](#12-biểu-đồ-cộng-tác---tạo-bài-viết-thảo-luận)
2. [Nghiệp Vụ 2: Làm Bài Ôn Tập Trắc Nghiệm & Chẩn Đoán Bẫy Tư Duy AI](#2-nghiệp-vụ-2-làm-bài-ôn-tập-trắc-nghiệm--chẩn-đoán-bẫy-tư-duy-ai)
   - [2.1. Biểu đồ tuần tự (Sequence Diagram)](#21-biểu-đồ-tuần-tự---làm-bài-thi--chẩn-đoán-bẫy-tư-duy)
   - [2.2. Biểu đồ cộng tác (Collaboration Diagram)](#22-biểu-đồ-cộng-tác---làm-bài-thi--chẩn-đoán-bẫy-tư-duy)
3. [Mã Nguồn PlantUML Chuẩn Form Báo Cáo](#3-mã-nguồn-plantuml-chuẩn-form-báo-cáo)

---

# 1. NGHIỆP VỤ 1: TẠO BÀI VIẾT THẢO LUẬN HỌC TẬP & TỰ ĐỘNG ĐỊNH DANH MÔN HỌC

### 📌 Mô tả nghiệp vụ:
Người học (Sinh viên / Giảng viên) mở khung soạn thảo phong cách cộng đồng học thuật, nhập tiêu đề, nội dung (kèm khối code, mẹo né bẫy, trắc nghiệm mini, trích dẫn tài liệu) và chọn hoặc tự gõ tên môn học bất kỳ. Hệ thống tự động xác thực, liên kết hoặc khởi tạo môn học mới vào CSDL nếu chưa có và đăng tải bài viết lên diễn đàn.

---

## 1.1. Biểu Đồ Tuần Tự - Tạo Bài Viết Thảo Luận
*(Vẽ theo chuẩn phân tích BCE: Actor $\rightarrow$ Boundary $\rightarrow$ Control $\rightarrow$ Entity $\rightarrow$ Database)*

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng<br/>(Sinh viên / Giảng viên)
    participant UI as 🖥️ Giao diện thảo luận<br/>(community.html)
    participant Ctrl as ⚙️ Xử lý bài viết<br/>(CommunityServlet)
    participant TopicEnt as 📚 Môn học<br/>(TopicDAO)
    participant PostEnt as 📝 Bài thảo luận<br/>(CommunityDAO)
    participant DB as 🗄️ CSDL<br/>(PostgreSQL)

    %% 1 & 2: Mở modal và gửi thông tin
    User ->> UI: 1. Yêu cầu tạo bài thảo luận
    activate UI
    UI -->> User: 1.1. Hiển thị modal soạn thảo học thuật (Facebook Style)
    deactivate UI

    User ->> UI: 2. Nhập thông tin & bấm "Đăng bài"<br/>(tiêu đề, nội dung, kênh, tên môn học/mã môn, mood)
    activate UI
    UI ->> Ctrl: 3. Gửi yêu cầu tạo bài viết (POST /api/community/posts)
    activate Ctrl

    %% Kiểm tra tính hợp lệ cơ bản
    alt Dữ liệu không hợp lệ (Tiêu đề hoặc nội dung để trống)
        Ctrl -->> UI: 4. Trả kết quả lỗi (HTTP 400: Thiếu tiêu đề/nội dung)
        UI -->> User: 4.1. Hiển thị thông báo lỗi yêu cầu điền đầy đủ
    else Dữ liệu hợp lệ
        %% 5: Kiểm tra và xử lý môn học
        Ctrl ->> TopicEnt: 5. Kiểm tra thông tin môn học (topicId, topicName)
        activate TopicEnt
        TopicEnt ->> DB: 5.1. Truy vấn môn học theo ID hoặc tên
        activate DB
        DB -->> TopicEnt: 5.2. Trả về kết quả tìm kiếm
        deactivate DB

        alt Môn học đã tồn tại trong CSDL
            TopicEnt -->> Ctrl: 6. Trả về mã môn học hiện có (topicId)
        else Môn học chưa tồn tại (Người dùng tự nhập môn mới)
            TopicEnt ->> DB: 6.1. Thêm mới môn học vào bảng topics (INSERT)
            activate DB
            DB -->> TopicEnt: 6.2. Trả về mã môn học mới vừa tạo
            deactivate DB
            TopicEnt -->> Ctrl: 6.3. Trả về mã môn học mới (topicId)
        end
        deactivate TopicEnt

        %% 7: Lưu bài thảo luận
        Ctrl ->> PostEnt: 7. Lưu bài thảo luận (userId, topicId, channel, title, content)
        activate PostEnt
        PostEnt ->> DB: 7.1. Ghi nhận bài thảo luận vào bảng community_posts (INSERT)
        activate DB

        alt Lưu bài viết thành công
            DB -->> PostEnt: 8. Lưu thành công (trả về postId mới)
            deactivate DB
            PostEnt -->> Ctrl: 8.1. Thông báo tạo bài thành công (postId)
            deactivate PostEnt
            Ctrl -->> UI: 9. Trả kết quả thành công (HTTP 200, dữ liệu bài viết)
            deactivate Ctrl
            UI -->> User: 10. Đóng modal, hiển thị thông báo thành công & cập nhật bài lên bảng tin
        else Lưu vào CSDL thất bại (Lỗi hệ thống)
            activate PostEnt
            activate DB
            DB -->> PostEnt: 8. Báo lỗi ghi dữ liệu (SQLException)
            deactivate DB
            PostEnt -->> Ctrl: 8.1. Thông báo lưu thất bại
            deactivate PostEnt
            activate Ctrl
            Ctrl -->> UI: 9. Trả kết quả lỗi máy chủ (HTTP 500)
            deactivate Ctrl
            UI -->> User: 10. Hiển thị thông báo lỗi và yêu cầu thử lại
        end
    end
    deactivate UI
```

---

## 1.2. Biểu Đồ Cộng Tác - Tạo Bài Viết Thảo Luận
*(Thiết kế chuẩn cấu trúc 4 đỉnh liên kết với các thông điệp có hướng và số thứ tự phân cấp y hệt Form Hình 2)*

### Sơ đồ luồng cộng tác (Mermaid Graph):

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

### Bản vẽ ký hiệu hình học trực quan (ASCII Art Layout y hệt Form Hình 2):

```
+--------------------+        1.1: Hiển thị form (◀)        +--------------------+
|                    | <----------------------------------  |                    |
|    :Người dùng     |                                      |     :Giao diện     |
| (Sinh viên/G.Viên) |  --------------------------------->  |  (community.html)  |
|                    |     1: Yêu cầu mở form tạo bài (➔)    |                    |
+--------------------+   1.2: Nhập thông tin bài viết (➔)   +--------------------+
                                1.3: Thông báo thành công (◀)          |         ▲
                                                                       |         |
                                         1.2: Gửi thông tin bài viết (▼)         | 1.2.1: Tiếp nhận & kiểm tra (▲)
                                                                       |         | 1.2.3: Trả kết quả xử lý (▲)
                                                                       ▼         |
+--------------------+    1.2.1.1: Kiểm tra/Tạo môn học (◀) +--------------------+
|                    | <----------------------------------  |                    |
|   :Cơ sở dữ liệu   |    1.2.1.2: Lưu bài thảo luận (◀)    | :Hệ thống điều khiển|
|    (PostgreSQL)    |                                      | (CommunityServlet  |
|                    |  --------------------------------->  |  TopicDAO, PostDAO)|
+--------------------+   1.2.2: Trả kết quả ghi CSDL (➔)    +--------------------+
```

---

# 2. NGHIỆP VỤ 2: LÀM BÀI ÔN TẬP TRẮC NGHIỆP & CHẨN ĐOÁN BẪY TƯ DUY AI

### 📌 Mô tả nghiệp vụ:
Sinh viên chọn bài ôn tập, trả lời các câu hỏi và gắn nhãn mức độ tự tin (`CERTAIN` / `GUESS`). Khi nộp bài, hệ thống chấm điểm, lưu lịch sử và kích hoạt Google Gemini AI chẩn đoán 4 nhóm lỗ hổng nhận thức (`syntax_swap`, `boundary_blindness`, `mental_model_gap`, `logic_flaw`), sau đó sinh bài học phục hồi kiến thức thích ứng.

---

## 2.1. Biểu Đồ Tuần Tự - Làm Bài Thi & Chẩn Đoán Bẫy Tư Duy
*(Vẽ theo chuẩn phân tích BCE + AI Engine)*

```mermaid
sequenceDiagram
    autonumber
    actor SV as 👨‍🎓 Sinh viên
    participant UI as 🖥️ Giao diện bài thi<br/>(quiz.html)
    participant Ctrl as ⚙️ Xử lý bài thi<br/>(QuizServlet & QuizService)
    participant AI as 🧠 Trợ lý AI<br/>(AIService & Gemini)
    participant Lesson as 📖 Bài học củng cố<br/>(RemedialLesson)
    participant DB as 🗄️ CSDL<br/>(PostgreSQL)

    SV ->> UI: 1. Bắt đầu làm bài trắc nghiệm
    activate UI
    UI ->> Ctrl: 2. Yêu cầu bộ câu hỏi theo chủ đề (POST /api/quiz/start)
    activate Ctrl
    Ctrl ->> DB: 2.1. Truy vấn danh sách câu hỏi & tạo phiên thi mới
    activate DB
    DB -->> Ctrl: 2.2. Trả về phiên thi (sessionId) và các câu hỏi
    deactivate DB
    Ctrl -->> UI: 3. Trả về đề thi và bộ đếm thời gian
    deactivate Ctrl
    UI -->> SV: 4. Hiển thị câu hỏi + bộ chọn độ tự tin (Chắc chắn / Đoán mò)
    deactivate UI

    SV ->> UI: 5. Chọn đáp án, gắn thẻ tự tin & bấm "Nộp bài"
    activate UI
    UI ->> Ctrl: 6. Gửi danh sách câu trả lời (POST /api/quiz/submit)
    activate Ctrl

    Ctrl ->> DB: 6.1. Lưu các câu trả lời vào bảng user_answers & tính điểm
    activate DB
    DB -->> Ctrl: 6.2. Xác nhận lưu kết quả bài thi
    deactivate DB

    %% Phân nhánh chẩn đoán AI
    alt Có câu trả lời sai hoặc đoán mò (Cần chẩn đoán bẫy nhận thức)
        Ctrl ->> AI: 7. Gửi ngữ cảnh câu sai/đoán mò yêu cầu phân tích sư phạm
        activate AI
        AI ->> AI: 7.1. Chẩn đoán 4 nhóm bẫy tư duy (Gemini 3.6 Flash)
        AI -->> Ctrl: 7.2. Trả về cấu trúc JSON bài học củng cố
        deactivate AI
        Ctrl ->> Lesson: 8. Đóng gói bài học củng cố (RemedialLesson)
        activate Lesson
        Lesson ->> DB: 8.1. Lưu bài học vào bảng remedial_lessons
        activate DB
        DB -->> Lesson: 8.2. Xác nhận lưu bài học
        deactivate DB
        Lesson -->> Ctrl: 8.3. Hoàn tất đóng gói
        deactivate Lesson
    else Làm đúng 100% với mức Chắc chắn
        Ctrl ->> Ctrl: 7. Bỏ qua bước gọi AI, xếp loại xuất sắc
    end

    Ctrl -->> UI: 9. Trả kết quả thi & phân tích củng cố (HTTP 200)
    deactivate Ctrl
    UI -->> SV: 10. Chuyển hướng sang result.html, hiển thị điểm số & thẻ bài học vượt bẫy
    deactivate UI
```

---

## 2.2. Biểu Đồ Cộng Tác - Làm Bài Thi & Chẩn Đoán Bẫy Tư Duy

### Sơ đồ luồng cộng tác (Mermaid Graph):

```mermaid
flowchart TD
    subgraph TopRow [" "]
        direction LR
        SV[":Sinh viên"]
        GD[":Giao diện bài thi<br/>(quiz.html & result.html)"]
    end

    subgraph BottomRow [" "]
        direction LR
        CSDL[":Cơ sở dữ liệu<br/>(PostgreSQL)"]
        HTDK[":Hệ thống điều khiển & AI<br/>(QuizService & AIService)"]
    end

    %% Cạnh ngang trên
    SV ---|"1: Chọn môn & bắt đầu thi ➔<br/>◀ 1.1: Hiển thị câu hỏi & độ tự tin<br/>1.2: Nộp đáp án & mức tự tin ➔<br/>◀ 1.3: Hiển thị bảng điểm & bài học củng cố"| GD

    %% Cạnh dọc phải
    GD ---|"1.2: Gửi bài nộp chấm điểm ➔<br/>◀ 1.2.1: Tiếp nhận và xác thực session<br/>◀ 1.2.3: Trả về kết quả thi & phân tích bẫy"| HTDK

    %% Cạnh ngang dưới
    HTDK ---|"1.2.1.1: Chấm điểm & lưu câu trả lời ➔<br/>1.2.1.2: Gọi Gemini AI sinh bài học củng cố ➔<br/>1.2.1.3: Lưu bài học phục hồi vào CSDL ➔<br/>◀ 1.2.2: Xác nhận hoàn tất lưu trữ"| CSDL
```

### Bản vẽ ký hiệu hình học trực quan (ASCII Art Layout):

```
+--------------------+  1.1: Hiển thị đề thi & chọn tự tin (◀)  +--------------------+
|                    | <---------------------------------------  |                    |
|    :Sinh viên      |                                           |     :Giao diện     |
|                    |  -------------------------------------->  | (quiz.html/result) |
|                    |    1: Chọn đề thi & bắt đầu làm bài (➔)   |                    |
+--------------------+    1.2: Nộp bài & mức độ tự tin (➔)       +--------------------+
                               1.3: Hiển thị điểm & bài học AI (◀)         |         ▲
                                                                           |         |
                                             1.2: Gửi danh sách đáp án (▼) |         | 1.2.1: Chấm điểm bài nộp (▲)
                                                                           |         | 1.2.3: Trả kết quả & bài học (▲)
                                                                           ▼         |
+--------------------+    1.2.1.1: Ghi nhận user_answers (◀)     +--------------------+
|                    | <---------------------------------------  |                    |
|   :Cơ sở dữ liệu   |    1.2.1.2: Gọi AI phân tích bẫy tư duy   | :Hệ thống điều khiển|
|    (PostgreSQL)    |    1.2.1.3: Ghi nhận remedial_lessons (◀) |   & Trợ lý AI      |
|                    |  -------------------------------------->  | (QuizService,      |
+--------------------+    1.2.2: Xác nhận lưu trữ hoàn tất (➔)   |  AIService, Gemini)|
                                                                 +--------------------+
```

---

# 3. MÃ NGUỒN PLANTUML CHUẨN FORM BÁO CÁO

> 💡 **Hướng dẫn sử dụng:** Bạn có thể copy trực tiếp đoạn mã dưới đây vào trang [PlantText](https://www.planttext.com/) hoặc công cụ Visual Studio Code / StarUML để xuất ra file ảnh PNG / PDF chất lượng cao có đầy đủ các biểu tượng BCE đặc trưng (Actor, Boundary, Control, Entity, Database).

### 3.1. PlantUML - Biểu Đồ Tuần Tự Tạo Bài Thảo Luận:
```plantuml
@startuml
autonumber
skinparam style strictuml
skinparam sequenceMessageAlign center

actor "Người dùng" as User #E8D5F5
boundary "Giao diện đặt lịch/thảo luận" as UI #D5E8F5
control "Xử lý bài viết" as Ctrl #FFE6CC
entity "Chủ đề môn học" as Topic #D5F5E3
entity "Bài thảo luận" as Post #D5F5E3
database "CSDL (PostgreSQL)" as DB #DAE8FC

User -> UI : 1. Yêu cầu tạo bài thảo luận
activate UI
UI --> User : 1.1. Hiển thị form tạo bài viết
deactivate UI

User -> UI : 2. Gửi thông tin bài viết\n(tiêu đề, nội dung, kênh, tên môn học)
activate UI
UI -> Ctrl : 3. Gửi thông tin bài viết
activate Ctrl

Ctrl -> Topic : 4. Kiểm tra môn học\n(tên môn học, mã môn)
activate Topic
Topic -> DB : 4.1. Truy vấn môn học
activate DB
DB --> Topic : 4.2. Trả dữ liệu môn học
deactivate DB
Topic --> Ctrl : 5. Trả kết quả kiểm tra
deactivate Topic

alt Môn học đã tồn tại
    Ctrl -> Post : 6. Lưu bài thảo luận
    activate Post
    Post -> DB : 6.1. Ghi nhận bài thảo luận
    activate DB
    DB --> Post : 7. Lưu thành công (mã bài viết)
    deactivate DB
    Post --> Ctrl : 7.1. Thông báo tạo bài thành công
    deactivate Post
    Ctrl --> UI : 8. Trả kết quả thành công
    UI --> User : 9. Hiển thị bài viết trên diễn đàn
else Môn học chưa tồn tại (Người dùng tự gõ)
    Ctrl -> Topic : 6. Khởi tạo môn học mới
    activate Topic
    Topic -> DB : 6.1. Ghi nhận môn học mới
    activate DB
    DB --> Topic : 6.2. Trả về mã môn học mới
    deactivate DB
    Topic --> Ctrl : 6.3. Trả về môn học mới
    deactivate Topic
    Ctrl -> Post : 7. Lưu bài thảo luận với môn mới
    activate Post
    Post -> DB : 7.1. Ghi nhận bài thảo luận
    activate DB
    DB --> Post : 8. Lưu thành công
    deactivate DB
    Post --> Ctrl : 8.1. Thông báo thành công
    deactivate Post
    Ctrl --> UI : 9. Trả kết quả thành công
    UI --> User : 10. Hiển thị thông báo đăng bài thành công
else Dữ liệu không hợp lệ
    Ctrl --> UI : 5. Thông báo lỗi (tiêu đề/nội dung trống)
    UI --> User : 6. Yêu cầu nhập lại thông tin
end

deactivate Ctrl
deactivate UI
@enduml
```

### 3.2. PlantUML - Biểu Đồ Cộng Tác Tạo Bài Thảo Luận:
```plantuml
@startuml
skinparam defaultTextAlignment center
skinparam rectangleCorner 10

rectangle ":Người dùng" as User
rectangle ":Giao diện" as UI
rectangle ":Hệ thống điều khiển" as Ctrl
rectangle ":Cơ sở dữ liệu" as DB

User -right- UI : 1: Yêu cầu mở form tạo bài >\n< 1.1: Hiển thị form\n1.2: Nhập thông tin bài viết >\n< 1.3: Thông báo thành công
UI -down- Ctrl : 1.2: Gửi thông tin bài viết v\n^ 1.2.1: Tiếp nhận và xác thực\n^ 1.2.3: Trả kết quả xử lý
Ctrl -left- DB : < 1.2.1.1: Kiểm tra/Tạo môn học\n< 1.2.1.2: Lưu bài thảo luận\n1.2.2: Trả kết quả ghi CSDL >

@enduml
```
