package com.lms.util;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Đọc biến cấu hình từ file .env ở thư mục gốc dự án.
 * Sử dụng thư viện dotenv-java.
 *
 * Ưu tiên: System Environment Variable > .env file > defaultValue
 */
public class ConfigLoader {

    private static final Dotenv dotenv;

    static {
        Dotenv d = null;
        String userDir = System.getProperty("user.dir", ".");
        String[] candidateDirs = {
            userDir,
            userDir + java.io.File.separator + "Hệ thống học tập thông minh",
            ".",
            "Hệ thống học tập thông minh",
            ".."
        };

        for (String dir : candidateDirs) {
            try {
                java.io.File envFile = new java.io.File(dir, ".env");
                if (envFile.exists() && envFile.isFile()) {
                    d = Dotenv.configure()
                            .directory(envFile.getParent())
                            .ignoreIfMissing()
                            .load();
                    System.out.println("[ConfigLoader] ✅ Đã tải file .env từ: " + envFile.getAbsolutePath());
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (d == null) {
            d = Dotenv.configure().ignoreIfMissing().load();
        }
        dotenv = d;
    }

    /**
     * Lấy giá trị biến cấu hình.
     * @param key Tên biến (ví dụ: GEMINI_API_KEY)
     * @return Giá trị hoặc null nếu không tìm thấy
     */
    public static String get(String key) {
        // Ưu tiên biến môi trường hệ thống trước
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return dotenv.get(key);
    }

    /**
     * Lấy giá trị biến cấu hình với giá trị mặc định.
     * @param key          Tên biến
     * @param defaultValue Giá trị mặc định nếu không tìm thấy
     * @return Giá trị hoặc defaultValue
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * Lấy giá trị kiểu int.
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
