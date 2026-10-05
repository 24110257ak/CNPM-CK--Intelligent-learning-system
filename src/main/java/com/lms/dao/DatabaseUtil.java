package com.lms.dao;

import com.lms.util.ConfigLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý Connection Pool kết nối SQL Server bằng HikariCP.
 * Đọc cấu hình từ .env thông qua ConfigLoader.
 *
 * Connection String mặc định:
 *   jdbc:sqlserver://localhost:1433;databaseName=lms_db;trustServerCertificate=true;encrypt=true
 */
public class DatabaseUtil {

    private static HikariDataSource dataSource;

    static {
        try {
            HikariConfig config = new HikariConfig();

            // ── Tự động nhận diện Driver: PostgreSQL (Neon.tech) hoặc SQL Server ──
            String dbUrl = ConfigLoader.get("DB_URL",
                    "jdbc:postgresql://ep-mute-queen-b3xtkksd-pooler.c-4.ap-southeast-1.aws.neon.tech/lms_db?sslmode=require");
            String username = ConfigLoader.get("DB_USERNAME", "lms_db_owner");
            String password = ConfigLoader.get("DB_PASSWORD", "npg_r4vyIfJa9tSX");

            if (dbUrl.contains("postgresql")) {
                Class.forName("org.postgresql.Driver");
                config.setDriverClassName("org.postgresql.Driver");
            } else {
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            }

            // ── Connection String từ .env ──
            config.setJdbcUrl(dbUrl);
            config.setUsername(username);
            config.setPassword(password);

            // ── Pool Settings ──
            config.setMaximumPoolSize(ConfigLoader.getInt("DB_POOL_SIZE", 10));
            config.setMinimumIdle(2);
            config.setIdleTimeout(300_000);       // 5 phút
            config.setConnectionTimeout(20_000);  // 20 giây
            config.setMaxLifetime(1_200_000);     // 20 phút

            // ── Performance Optimizations ──
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            // ── Pool Name (hiển thị trong log) ──
            config.setPoolName("LMS-HikariPool");

            dataSource = new HikariDataSource(config);

            System.out.println("[DatabaseUtil] ✅ HikariCP Pool khởi tạo thành công: " + config.getJdbcUrl());

            // Tự động kiểm tra và nâng cấp/khởi tạo schema nếu cần
            checkAndInitializeSchema();

        } catch (Exception e) {
            System.err.println("[DatabaseUtil] ❌ Lỗi khởi tạo HikariCP Pool!");
            e.printStackTrace();
        }
    }

    /**
     * Tự động khởi tạo schema.sql nếu CSDL hoàn toàn mới,
     * sau đó kiểm tra và nâng cấp schema (Auto-Migration).
     */
    private static void checkAndInitializeSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String dbProductName = conn.getMetaData().getDatabaseProductName().toLowerCase();
            boolean isPostgres = dbProductName.contains("postgres");

            boolean tablesExist = false;
            String checkTableSql = isPostgres
                    ? "SELECT 1 FROM information_schema.tables WHERE table_name = 'topics'"
                    : "SELECT 1 FROM sys.tables WHERE name = 'topics'";

            try (var rs = stmt.executeQuery(checkTableSql)) {
                if (rs.next()) {
                    tablesExist = true;
                }
            }

            if (!tablesExist) {
                System.out.println("[DatabaseUtil] 📦 CSDL mới chưa có bảng. Đang tự động nạp cấu trúc từ schema.sql...");
                try (var in = DatabaseUtil.class.getResourceAsStream("/db/schema.sql")) {
                    if (in != null) {
                        String fullSql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        stmt.execute(fullSql);
                        System.out.println("[DatabaseUtil] 🎉 Tự động nạp CSDL thành công từ schema.sql!");
                    }
                }
            }

            // Tiếp tục chạy auto-migration nếu cần
            checkAndMigrateSchema();

        } catch (Exception e) {
            System.err.println("[DatabaseUtil] ⚠️ Cảnh báo khởi tạo CSDL: " + e.getMessage());
        }
    }

    /**
     * Tự động kiểm tra và thêm cột [misconception_tag] nếu CSDL chưa có.
     * Hỗ trợ PostgreSQL (Neon.tech) và SQL Server.
     */
    private static void checkAndMigrateSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String dbProductName = conn.getMetaData().getDatabaseProductName().toLowerCase();

            if (dbProductName.contains("postgres")) {
                // PostgreSQL / Neon.tech migration
                String pgCheckColumnSql = "DO $$ "
                        + "BEGIN "
                        + "    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='questions') THEN "
                        + "        IF NOT EXISTS ( "
                        + "            SELECT 1 FROM information_schema.columns "
                        + "            WHERE table_name='questions' AND column_name='misconception_tag' "
                        + "        ) THEN "
                        + "            ALTER TABLE questions ADD COLUMN misconception_tag VARCHAR(50); "
                        + "        END IF; "
                        + "    END IF; "
                        + "END $$;";

                String pgSeedTagsSql = "UPDATE questions SET misconception_tag = CASE (question_id % 4) "
                        + "    WHEN 0 THEN 'syntax_swap' "
                        + "    WHEN 1 THEN 'boundary_blindness' "
                        + "    WHEN 2 THEN 'mental_model_gap' "
                        + "    ELSE 'logic_flaw' END "
                        + "WHERE misconception_tag IS NULL";

                stmt.execute(pgCheckColumnSql);
                try {
                    stmt.execute(pgSeedTagsSql);
                } catch (Exception ignored) {}

                // PostgreSQL: Bổ sung cột created_by cho bảng topics để phân quyền người tạo môn học
                String pgTopicCreatedBy = "DO $$ "
                        + "BEGIN "
                        + "    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='topics') THEN "
                        + "        IF NOT EXISTS ( "
                        + "            SELECT 1 FROM information_schema.columns "
                        + "            WHERE table_name='topics' AND column_name='created_by' "
                        + "        ) THEN "
                        + "            ALTER TABLE topics ADD COLUMN created_by INT NULL REFERENCES users(user_id) ON DELETE SET NULL; "
                        + "        END IF; "
                        + "    END IF; "
                        + "END $$;";
                try {
                    stmt.execute(pgTopicCreatedBy);
                } catch (Exception ignored) {}

                // PostgreSQL: Bảng thảo luận & bình luận câu hỏi (Forum)
                stmt.execute("CREATE TABLE IF NOT EXISTS question_comments ("
                        + "comment_id SERIAL PRIMARY KEY, "
                        + "question_id INT NOT NULL REFERENCES questions(question_id) ON DELETE CASCADE, "
                        + "user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, "
                        + "parent_comment_id INT NULL REFERENCES question_comments(comment_id) ON DELETE CASCADE, "
                        + "content TEXT NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");

                // PostgreSQL: Bảng đánh giá độ tin cậy / Upvote / Báo lỗi
                stmt.execute("CREATE TABLE IF NOT EXISTS question_ratings ("
                        + "rating_id SERIAL PRIMARY KEY, "
                        + "question_id INT NOT NULL REFERENCES questions(question_id) ON DELETE CASCADE, "
                        + "user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, "
                        + "rating_type VARCHAR(20) NOT NULL, "
                        + "report_reason TEXT NULL, "
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "CONSTRAINT UQ_question_user_rating UNIQUE (question_id, user_id)"
                        + ");");

                // PostgreSQL: Bảng Diễn Đàn & Không Gian Cộng Đồng Học Tập (Discord / Facebook style)
                stmt.execute("CREATE TABLE IF NOT EXISTS community_posts ("
                        + "post_id SERIAL PRIMARY KEY, "
                        + "user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, "
                        + "topic_id INT NULL REFERENCES topics(topic_id) ON DELETE SET NULL, "
                        + "channel VARCHAR(50) NOT NULL DEFAULT 'general', "
                        + "title VARCHAR(255) NOT NULL, "
                        + "content TEXT NOT NULL, "
                        + "likes_count INT NOT NULL DEFAULT 0, "
                        + "comments_count INT NOT NULL DEFAULT 0, "
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");

                stmt.execute("CREATE TABLE IF NOT EXISTS community_post_comments ("
                        + "comment_id SERIAL PRIMARY KEY, "
                        + "post_id INT NOT NULL REFERENCES community_posts(post_id) ON DELETE CASCADE, "
                        + "user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, "
                        + "content TEXT NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");

                stmt.execute("CREATE TABLE IF NOT EXISTS community_post_likes ("
                        + "like_id SERIAL PRIMARY KEY, "
                        + "post_id INT NOT NULL REFERENCES community_posts(post_id) ON DELETE CASCADE, "
                        + "user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, "
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "CONSTRAINT UQ_community_post_like UNIQUE (post_id, user_id)"
                        + ");");

                // Seed bài viết mẫu nếu bảng mới được tạo
                try (var countRs = stmt.executeQuery("SELECT COUNT(*) FROM community_posts")) {
                    if (countRs.next() && countRs.getInt(1) == 0) {
                        stmt.execute("INSERT INTO community_posts (user_id, topic_id, channel, title, content, likes_count, comments_count) "
                                + "SELECT u.user_id, 1, 'tips', 'Kinh nghiệm né bẫy Boundary Blindness khi viết vòng lặp For trong Java', "
                                + "'Chào mọi người, khi làm trắc nghiệm dạng biên mảng [i <= array.length] các bạn nhớ chú ý mảng trong Java bắt đầu từ 0 đến length - 1. Nếu dùng <= sẽ vướng lỗi ArrayIndexOutOfBoundsException kinh điển!', 5, 1 "
                                + "FROM users u WHERE u.role = 'teacher' LIMIT 1;");

                        stmt.execute("INSERT INTO community_posts (user_id, topic_id, channel, title, content, likes_count, comments_count) "
                                + "SELECT u.user_id, 1, 'qna', 'Phân biệt giữa Interface và Abstract Class khi nào nên dùng cái nào?', "
                                + "'Mình đang làm bài tập lớn phần hướng đối tượng, theo các bạn khi thiết kế kiến trúc đa tầng (Clean Architecture), tầng Repository nên dùng Interface hay Abstract class thì linh hoạt hơn cho unit test?', 3, 0 "
                                + "FROM users u WHERE u.role = 'student' LIMIT 1;");
                    }
                } catch (Exception ignored) {}

                System.out.println("[DatabaseUtil] ✅ PostgreSQL Auto-Migration: Cột [misconception_tag] và bảng [question_comments, community_posts] đã sẵn sàng!");

            } else {
                // SQL Server migration
                String checkColumnSql = "IF NOT EXISTS (\n"
                        + "    SELECT * FROM sys.columns \n"
                        + "    WHERE object_id = OBJECT_ID('questions') AND name = 'misconception_tag'\n"
                        + ")\n"
                        + "BEGIN\n"
                        + "    ALTER TABLE questions ADD misconception_tag NVARCHAR(50) NULL;\n"
                        + "END";

                String seedTagsSql = "UPDATE questions SET misconception_tag = CASE (question_id % 4) "
                        + "    WHEN 0 THEN N'syntax_swap' "
                        + "    WHEN 1 THEN N'boundary_blindness' "
                        + "    WHEN 2 THEN N'mental_model_gap' "
                        + "    ELSE N'logic_flaw' END "
                        + "WHERE misconception_tag IS NULL";

                String checkConstraintSql = "IF EXISTS (SELECT * FROM sys.check_constraints WHERE name = 'CK_user_answers_answer')\n"
                        + "BEGIN\n"
                        + "    ALTER TABLE user_answers DROP CONSTRAINT CK_user_answers_answer;\n"
                        + "    ALTER TABLE user_answers ADD CONSTRAINT CK_user_answers_answer CHECK (user_answer IN (N'A', N'B', N'C', N'D', N'', N' '));\n"
                        + "END";

                String sqlServerDiscussionTables = "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'question_comments')\n"
                        + "BEGIN\n"
                        + "    CREATE TABLE question_comments (\n"
                        + "        comment_id INT IDENTITY(1,1) PRIMARY KEY,\n"
                        + "        question_id INT NOT NULL FOREIGN KEY REFERENCES questions(question_id) ON DELETE CASCADE,\n"
                        + "        user_id INT NOT NULL FOREIGN KEY REFERENCES users(user_id) ON DELETE CASCADE,\n"
                        + "        parent_comment_id INT NULL FOREIGN KEY REFERENCES question_comments(comment_id),\n"
                        + "        content NVARCHAR(MAX) NOT NULL,\n"
                        + "        created_at DATETIME DEFAULT GETDATE()\n"
                        + "    );\n"
                        + "END;\n"
                        + "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'question_ratings')\n"
                        + "BEGIN\n"
                        + "    CREATE TABLE question_ratings (\n"
                        + "        rating_id INT IDENTITY(1,1) PRIMARY KEY,\n"
                        + "        question_id INT NOT NULL FOREIGN KEY REFERENCES questions(question_id) ON DELETE CASCADE,\n"
                        + "        user_id INT NOT NULL FOREIGN KEY REFERENCES users(user_id) ON DELETE CASCADE,\n"
                        + "        rating_type VARCHAR(20) NOT NULL,\n"
                        + "        report_reason NVARCHAR(MAX) NULL,\n"
                        + "        created_at DATETIME DEFAULT GETDATE(),\n"
                        + "        CONSTRAINT UQ_question_user_rating UNIQUE (question_id, user_id)\n"
                        + "    );\n"
                        + "END;";

                stmt.execute(checkColumnSql);
                stmt.execute(seedTagsSql);
                try {
                    stmt.execute(checkConstraintSql);
                } catch (Exception ignored) {}
                try {
                    stmt.execute(sqlServerDiscussionTables);
                } catch (Exception ignored) {}

                String sqlServerTopicCreatedBy = "IF NOT EXISTS (\n"
                        + "    SELECT * FROM sys.columns \n"
                        + "    WHERE object_id = OBJECT_ID('topics') AND name = 'created_by'\n"
                        + ")\n"
                        + "BEGIN\n"
                        + "    ALTER TABLE topics ADD created_by INT NULL FOREIGN KEY REFERENCES users(user_id) ON DELETE SET NULL;\n"
                        + "END";
                try {
                    stmt.execute(sqlServerTopicCreatedBy);
                } catch (Exception ignored) {}

                System.out.println("[DatabaseUtil] ✅ SQL Server Auto-Migration: Cột [misconception_tag, created_by] và bảng [question_comments, question_ratings] đã sẵn sàng!");
            }
        } catch (Exception e) {
            System.err.println("[DatabaseUtil] ⚠️ Cảnh báo Auto-Migration: " + e.getMessage());
        }
    }

    /**
     * Lấy một Connection từ pool. PHẢI đóng sau khi dùng (try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource chưa được khởi tạo. Kiểm tra cấu hình .env và SQL Server.");
        }
        return dataSource.getConnection();
    }

    /**
     * Kiểm tra pool còn hoạt động không.
     */
    public static boolean isHealthy() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Đóng pool khi ứng dụng shutdown.
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DatabaseUtil] 🔒 HikariCP Pool đã đóng.");
        }
    }
}
