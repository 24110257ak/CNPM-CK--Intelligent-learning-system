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

            // ── MSSQL JDBC Driver ──
            config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            // ── Connection String từ .env ──
            config.setJdbcUrl(ConfigLoader.get("DB_URL",
                    "jdbc:sqlserver://localhost:1433;databaseName=lms_db;trustServerCertificate=true;encrypt=true"));
            config.setUsername(ConfigLoader.get("DB_USERNAME", "sa"));
            config.setPassword(ConfigLoader.get("DB_PASSWORD", ""));

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

            // Tự động kiểm tra và nâng cấp schema nếu cần
            checkAndMigrateSchema();

        } catch (Exception e) {
            System.err.println("[DatabaseUtil] ❌ Lỗi khởi tạo HikariCP Pool!");
            e.printStackTrace();
        }
    }

    /**
     * Tự động kiểm tra và thêm cột [misconception_tag] nếu CSDL chưa có.
     * Gắn nhãn phân loại lỗi tư duy cho các câu hỏi hiện có.
     */
    private static void checkAndMigrateSchema() {
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

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(checkColumnSql);
            stmt.execute(seedTagsSql);
            try {
                stmt.execute(checkConstraintSql);
            } catch (Exception ignored) {}
            System.out.println("[DatabaseUtil] ✅ Auto-Migration: Cột [misconception_tag] đã sẵn sàng!");
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
