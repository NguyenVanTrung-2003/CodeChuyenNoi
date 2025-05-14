package org.example.codechuyennoi.Workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class BackgroundManager {
    private static final Logger logger = LoggerFactory.getLogger(BackgroundManager.class);
    private final String backgroundPath;

    public BackgroundManager(String backgroundPath) {
        this.backgroundPath = backgroundPath;
    }

    public String loadBackground() {
        try {
            logger.info("Đang tải tài nguyên nền từ: {}", backgroundPath);
            File backgroundDir = new File(backgroundPath);
            if (!backgroundDir.exists() || !backgroundDir.isDirectory()) {
                logger.error("Thư mục nền không tồn tại: {}", backgroundPath);
                return null;
            }
            // Giả định chọn ngẫu nhiên một file hình ảnh hoặc video
            File[] files = backgroundDir.listFiles((dir, name) -> name.endsWith(".mp4") || name.endsWith(".jpg"));
            if (files == null || files.length == 0) {
                logger.error("Không tìm thấy tài nguyên nền");
                return null;
            }
            return files[0].getAbsolutePath();
        } catch (Exception e) {
            logger.error("Lỗi khi tải tài nguyên nền: {}", e.getMessage());
            return null;
        }
    }
}