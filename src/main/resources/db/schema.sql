-- ═══════════════════════════════════════════════════════════════════════════════
-- HỆ THỐNG HỌC TẬP THÔNG MINH — Database Schema (SQL Server / SSMS)
-- Chuẩn hóa 3NF | NVARCHAR hỗ trợ tiếng Việt | CHECK thay ENUM
-- ═══════════════════════════════════════════════════════════════════════════════

-- Tạo Database (chạy 1 lần duy nhất)
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'lms_db')
BEGIN
    CREATE DATABASE lms_db;
END
GO

USE lms_db;
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 1: USERS (Người dùng)
-- ─────────────────────────────────────────────────────────────────────────────
IF OBJECT_ID('dbo.chat_history', 'U') IS NOT NULL DROP TABLE dbo.chat_history;
IF OBJECT_ID('dbo.remedial_lessons', 'U') IS NOT NULL DROP TABLE dbo.remedial_lessons;
IF OBJECT_ID('dbo.user_answers', 'U') IS NOT NULL DROP TABLE dbo.user_answers;
IF OBJECT_ID('dbo.quiz_sessions', 'U') IS NOT NULL DROP TABLE dbo.quiz_sessions;
IF OBJECT_ID('dbo.questions', 'U') IS NOT NULL DROP TABLE dbo.questions;
IF OBJECT_ID('dbo.topics', 'U') IS NOT NULL DROP TABLE dbo.topics;
IF OBJECT_ID('dbo.users', 'U') IS NOT NULL DROP TABLE dbo.users;
GO

CREATE TABLE users (
    user_id         INT IDENTITY(1,1) PRIMARY KEY,
    username        NVARCHAR(50)   NOT NULL UNIQUE,
    password_hash   NVARCHAR(255)  NOT NULL,
    full_name       NVARCHAR(100)  NOT NULL,
    email           NVARCHAR(100)  NULL,
    role            NVARCHAR(20)   NOT NULL DEFAULT N'student'
                    CONSTRAINT CK_users_role CHECK (role IN (N'student', N'teacher', N'admin')),
    interests       NVARCHAR(MAX)  NULL,       -- JSON: sở thích để AI cá nhân hóa ẩn dụ
    created_at      DATETIME2      NOT NULL DEFAULT GETDATE(),
    updated_at      DATETIME2      NOT NULL DEFAULT GETDATE()
);
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 2: TOPICS (Chủ đề học tập)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE topics (
    topic_id        INT IDENTITY(1,1) PRIMARY KEY,
    topic_name      NVARCHAR(255)  NOT NULL,
    description     NVARCHAR(MAX)  NULL,
    parent_topic_id INT            NULL,
    display_order   INT            NOT NULL DEFAULT 0,
    created_at      DATETIME2      NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_topics_parent FOREIGN KEY (parent_topic_id) REFERENCES topics(topic_id)
);
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 3: QUESTIONS (Ngân hàng câu hỏi)
-- Cột explanation = AI Fallback Buffer (khi Gemini API không khả dụng)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE questions (
    question_id     INT IDENTITY(1,1) PRIMARY KEY,
    topic_id        INT            NOT NULL,
    question_text   NVARCHAR(MAX)  NOT NULL,
    option_a        NVARCHAR(MAX)  NOT NULL,
    option_b        NVARCHAR(MAX)  NOT NULL,
    option_c        NVARCHAR(MAX)  NOT NULL,
    option_d        NVARCHAR(MAX)  NOT NULL,
    correct_answer  NCHAR(1)       NOT NULL
                    CONSTRAINT CK_questions_answer CHECK (correct_answer IN (N'A', N'B', N'C', N'D')),
    explanation     NVARCHAR(MAX)  NULL,       -- ★ AI Fallback Buffer: giải thích cơ bản khi API offline
    difficulty      NVARCHAR(10)   NOT NULL DEFAULT N'medium'
                    CONSTRAINT CK_questions_difficulty CHECK (difficulty IN (N'easy', N'medium', N'hard')),
    misconception_tag NVARCHAR(50) NULL,       -- ★ Phân loại lỗi: syntax_swap, boundary_blindness, mental_model_gap, logic_flaw
    created_at      DATETIME2      NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_questions_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id)
);
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 4: QUIZ_SESSIONS (Phiên làm bài)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE quiz_sessions (
    session_id      INT IDENTITY(1,1) PRIMARY KEY,
    user_id         INT            NOT NULL,
    topic_id        INT            NOT NULL,
    total_questions INT            NOT NULL,
    correct_count   INT            NOT NULL DEFAULT 0,
    score           FLOAT          NOT NULL DEFAULT 0.0,
    started_at      DATETIME2      NOT NULL DEFAULT GETDATE(),
    completed_at    DATETIME2      NULL,

    CONSTRAINT FK_quiz_sessions_user  FOREIGN KEY (user_id)  REFERENCES users(user_id),
    CONSTRAINT FK_quiz_sessions_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id)
);
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 5: USER_ANSWERS (Câu trả lời của sinh viên)
-- ★ confidence_level: Luồng "Confidence Tagging"
--   CERTAIN = chắc chắn, GUESS = đoán mò
--   Nếu đúng nhưng GUESS → AI vẫn kích hoạt để củng cố kiến thức
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE user_answers (
    answer_id       INT IDENTITY(1,1) PRIMARY KEY,
    session_id      INT            NOT NULL,
    question_id     INT            NOT NULL,
    user_answer     NCHAR(1)       NOT NULL
                    CONSTRAINT CK_user_answers_answer CHECK (user_answer IN (N'A', N'B', N'C', N'D')),
    is_correct      BIT            NOT NULL DEFAULT 0,
    confidence_level NVARCHAR(10)  NOT NULL DEFAULT N'CERTAIN'
                    CONSTRAINT CK_user_answers_confidence CHECK (confidence_level IN (N'CERTAIN', N'GUESS')),
    answered_at     DATETIME2      NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_user_answers_session  FOREIGN KEY (session_id)  REFERENCES quiz_sessions(session_id),
    CONSTRAINT FK_user_answers_question FOREIGN KEY (question_id) REFERENCES questions(question_id)
);
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 6: REMEDIAL_LESSONS (Bài học củng cố do AI tạo)
-- Gắn liền với câu trả lời sai cụ thể → Nguyên tắc Persistence
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE remedial_lessons (
    lesson_id       INT IDENTITY(1,1) PRIMARY KEY,
    answer_id       INT            NOT NULL,
    user_id         INT            NOT NULL,
    error_reason    NVARCHAR(MAX)  NULL,       -- AI phân tích nguyên nhân sai
    lesson_content  NVARCHAR(MAX)  NULL,       -- Bài giảng ngắn do AI sinh ra
    practice_question NVARCHAR(MAX) NULL,      -- JSON: câu hỏi luyện tập mới
    misconception_type NVARCHAR(30) NULL
                    CONSTRAINT CK_remedial_misconception CHECK (
                        misconception_type IN (N'syntax_swap', N'boundary_blindness', N'mental_model_gap', N'other')
                    ),
    created_at      DATETIME2      NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_remedial_answer FOREIGN KEY (answer_id) REFERENCES user_answers(answer_id),
    CONSTRAINT FK_remedial_user   FOREIGN KEY (user_id)   REFERENCES users(user_id)
);
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- BẢNG 7: CHAT_HISTORY (Lịch sử trò chuyện với AI Chatbot)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE chat_history (
    chat_id         INT IDENTITY(1,1) PRIMARY KEY,
    user_id         INT            NOT NULL,
    session_id      INT            NULL,       -- NULL nếu chat tự do (không gắn bài thi)
    user_message    NVARCHAR(MAX)  NOT NULL,
    ai_response     NVARCHAR(MAX)  NULL,
    persona         NVARCHAR(20)   NOT NULL DEFAULT N'peer_tutor'
                    CONSTRAINT CK_chat_persona CHECK (
                        persona IN (N'senior_dev', N'peer_tutor', N'professor')
                    ),
    created_at      DATETIME2      NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_chat_user    FOREIGN KEY (user_id)    REFERENCES users(user_id),
    CONSTRAINT FK_chat_session FOREIGN KEY (session_id) REFERENCES quiz_sessions(session_id)
);
GO


-- ═══════════════════════════════════════════════════════════════════════════════
-- DỮ LIỆU MẪU (SEED DATA)
-- 2 Chủ đề + 10 Câu hỏi (tiếng Việt) + 1 User demo
-- ═══════════════════════════════════════════════════════════════════════════════

-- ── User mẫu (password: "demo123" - BCrypt hash) ──
INSERT INTO users (username, password_hash, full_name, email, role, interests)
VALUES (
    N'sinhvien01',
    N'$2a$12$d6StoKa670Vwar0ogOmb3uEZUysC5bS4lJB1fIaMkv0iSrfaukZ2i',
    N'Nguyễn Văn An',
    N'an.nguyen@student.edu.vn',
    N'student',
    N'{"hobbies": ["game RPG", "cafe", "coding"], "learning_style": "visual"}'
);

INSERT INTO users (username, password_hash, full_name, email, role)
VALUES (
    N'giangvien01',
    N'$2a$12$d6StoKa670Vwar0ogOmb3uEZUysC5bS4lJB1fIaMkv0iSrfaukZ2i',
    N'Trần Thị Mai',
    N'mai.tran@teacher.edu.vn',
    N'teacher'
);
GO

-- ── Chủ đề 1: Java OOP ──
INSERT INTO topics (topic_name, description, display_order)
VALUES (
    N'Lập trình hướng đối tượng Java (OOP)',
    N'Các khái niệm cốt lõi: Lớp, Đối tượng, Kế thừa, Đa hình, Đóng gói, Trừu tượng hóa trong ngôn ngữ Java.',
    1
);

-- ── Chủ đề 2: Cấu trúc dữ liệu ──
INSERT INTO topics (topic_name, description, display_order)
VALUES (
    N'Cấu trúc dữ liệu cơ bản',
    N'Mảng, Danh sách liên kết, Stack, Queue, Cây nhị phân, Hash Table và các thuật toán liên quan.',
    2
);
GO

-- ══════════════════════════════════════════════════════════════════════════
-- CÂU HỎI CHỦ ĐỀ 1: Java OOP (topic_id = 1)
-- ══════════════════════════════════════════════════════════════════════════

-- Câu 1 (Easy)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (1,
    N'Từ khóa nào được sử dụng để kế thừa một lớp trong Java?',
    N'implements',
    N'extends',
    N'inherits',
    N'super',
    N'B',
    N'Từ khóa "extends" dùng để kế thừa một lớp (class) trong Java. "implements" dùng để triển khai một interface, không phải kế thừa lớp. "inherits" không phải từ khóa Java. "super" dùng để gọi phương thức/constructor của lớp cha.',
    N'easy'
);

-- Câu 2 (Easy)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (1,
    N'Đâu KHÔNG phải là một tính chất của lập trình hướng đối tượng (OOP)?',
    N'Tính kế thừa (Inheritance)',
    N'Tính đa hình (Polymorphism)',
    N'Tính lặp lại (Iteration)',
    N'Tính đóng gói (Encapsulation)',
    N'C',
    N'4 tính chất của OOP gồm: Kế thừa, Đa hình, Đóng gói, và Trừu tượng hóa (Abstraction). "Tính lặp lại" (Iteration) là khái niệm trong vòng lặp, không phải đặc trưng OOP.',
    N'easy'
);

-- Câu 3 (Medium)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (1,
    N'Khi một lớp con override (ghi đè) một phương thức của lớp cha, phạm vi truy cập của phương thức ghi đè phải:',
    N'Bằng hoặc rộng hơn phạm vi của lớp cha',
    N'Bằng hoặc hẹp hơn phạm vi của lớp cha',
    N'Bắt buộc phải giống hệt lớp cha',
    N'Không có quy tắc nào về phạm vi truy cập',
    N'A',
    N'Khi override phương thức, phạm vi truy cập (access modifier) phải bằng hoặc rộng hơn phạm vi của lớp cha. Ví dụ: nếu lớp cha dùng protected, lớp con có thể dùng protected hoặc public, nhưng KHÔNG được dùng private.',
    N'medium'
);

-- Câu 4 (Medium)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (1,
    N'Trong Java, lớp trừu tượng (abstract class) khác với interface ở điểm nào?',
    N'Abstract class có thể chứa phương thức có thân hàm (method body)',
    N'Interface không thể khai báo hằng số (constant)',
    N'Abstract class không hỗ trợ đa kế thừa, interface cũng không',
    N'Cả abstract class và interface đều bắt buộc có constructor',
    N'A',
    N'Abstract class có thể chứa cả phương thức trừu tượng (abstract) lẫn phương thức cụ thể (có method body). Interface từ Java 8 cũng hỗ trợ default method, nhưng truyền thống chỉ chứa method signature. Interface hỗ trợ đa kế thừa, abstract class thì không.',
    N'medium'
);

-- Câu 5 (Hard)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (1,
    N'Đoạn code sau sẽ in ra kết quả gì?
class Animal { void sound() { System.out.print("Animal "); } }
class Dog extends Animal { void sound() { System.out.print("Dog "); } }
Animal a = new Dog(); a.sound();',
    N'Animal',
    N'Dog',
    N'Animal Dog',
    N'Lỗi biên dịch (Compile Error)',
    N'B',
    N'Đây là ví dụ về Runtime Polymorphism (đa hình lúc chạy). Biến "a" có kiểu tham chiếu là Animal nhưng đối tượng thực tế là Dog. Khi gọi a.sound(), JVM sẽ gọi phương thức sound() của đối tượng thực tế (Dog), in ra "Dog".',
    N'hard'
);

-- ══════════════════════════════════════════════════════════════════════════
-- CÂU HỎI CHỦ ĐỀ 2: Cấu trúc dữ liệu (topic_id = 2)
-- ══════════════════════════════════════════════════════════════════════════

-- Câu 6 (Easy)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (2,
    N'Cấu trúc dữ liệu Stack hoạt động theo nguyên tắc nào?',
    N'FIFO (First In, First Out)',
    N'LIFO (Last In, First Out)',
    N'Random Access',
    N'Priority-based',
    N'B',
    N'Stack (Ngăn xếp) hoạt động theo nguyên tắc LIFO - phần tử được thêm vào cuối cùng sẽ được lấy ra đầu tiên, giống như xếp chồng đĩa. FIFO là nguyên tắc của Queue (Hàng đợi).',
    N'easy'
);

-- Câu 7 (Easy)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (2,
    N'Độ phức tạp thời gian khi truy cập phần tử theo chỉ số (index) trong mảng (Array) là:',
    N'O(n)',
    N'O(log n)',
    N'O(1)',
    N'O(n²)',
    N'C',
    N'Mảng (Array) cho phép truy cập ngẫu nhiên (Random Access) với độ phức tạp O(1) vì các phần tử được lưu liên tiếp trong bộ nhớ. Chỉ cần tính: địa chỉ = base + index * kích_thước_phần_tử.',
    N'easy'
);

-- Câu 8 (Medium)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (2,
    N'Trong Danh sách liên kết đơn (Singly Linked List), thao tác nào có độ phức tạp O(n)?',
    N'Thêm phần tử vào đầu danh sách',
    N'Xóa phần tử ở đầu danh sách',
    N'Tìm kiếm một phần tử theo giá trị',
    N'Lấy kích thước danh sách (nếu đã lưu biến size)',
    N'C',
    N'Tìm kiếm theo giá trị trong Linked List bắt buộc phải duyệt tuần tự từ đầu → cuối, tệ nhất duyệt hết n phần tử → O(n). Thêm/xóa ở đầu chỉ cần O(1) vì chỉ thay đổi con trỏ head.',
    N'medium'
);

-- Câu 9 (Medium)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (2,
    N'Hash Table xử lý xung đột (collision) bằng phương pháp nào sau đây?',
    N'Binary Search',
    N'Chaining (Nối chuỗi) hoặc Open Addressing',
    N'Bubble Sort',
    N'Breadth-First Search',
    N'B',
    N'Khi hai key khác nhau cho ra cùng một hash index (collision), hai phương pháp phổ biến để xử lý là: Chaining (mỗi slot chứa một danh sách liên kết) và Open Addressing (tìm slot trống tiếp theo theo quy tắc linear/quadratic probing).',
    N'medium'
);

-- Câu 10 (Hard)
INSERT INTO questions (topic_id, question_text, option_a, option_b, option_c, option_d, correct_answer, explanation, difficulty)
VALUES (2,
    N'Cho cây nhị phân tìm kiếm (BST) có các phần tử được chèn theo thứ tự: 50, 30, 70, 20, 40, 60, 80. Kết quả duyệt theo thứ tự giữa (In-order Traversal) là gì?',
    N'50, 30, 20, 40, 70, 60, 80',
    N'20, 30, 40, 50, 60, 70, 80',
    N'20, 40, 30, 60, 80, 70, 50',
    N'50, 30, 70, 20, 40, 60, 80',
    N'B',
    N'In-order Traversal của BST luôn cho kết quả là dãy số đã sắp xếp tăng dần. Quy tắc: duyệt cây con trái → gốc → cây con phải. Với BST trên: 20→30→40→50→60→70→80.',
    N'hard'
);
GO

-- ═══════════════════════════════════════════════════════════════════════════════
-- TẠO INDEX TỐI ƯU TRUY VẤN
-- ═══════════════════════════════════════════════════════════════════════════════
CREATE INDEX IX_questions_topic      ON questions(topic_id);
CREATE INDEX IX_quiz_sessions_user   ON quiz_sessions(user_id);
CREATE INDEX IX_quiz_sessions_topic  ON quiz_sessions(topic_id);
CREATE INDEX IX_user_answers_session ON user_answers(session_id);
CREATE INDEX IX_remedial_user        ON remedial_lessons(user_id);
CREATE INDEX IX_chat_history_user    ON chat_history(user_id);
CREATE INDEX IX_chat_history_session ON chat_history(session_id);
GO

PRINT N'═══ Schema tạo thành công! Database lms_db sẵn sàng. ═══';
GO
