package org.example.codechuyennoi.Workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Random;

public class BackgroundManager {
    private static final Logger logger = LoggerFactory.getLogger(BackgroundManager.class);
    private final String backgroundPath;

    /**
     * Constructor nhận đường dẫn thư mục nền. Nếu null hoặc rỗng, mặc định dùng "D:/anh".
     *
     * @param backgroundPath Đường dẫn đến thư mục chứa ảnh hoặc video nền.
     */
    public BackgroundManager(String backgroundPath) {
        if (backgroundPath == null || backgroundPath.trim().isEmpty()) {
            this.backgroundPath = "D:\\anhtrutien";
        } else {
            this.backgroundPath = backgroundPath;
        }
    }

    /**
     * Tải tài nguyên nền từ thư mục, chọn ngẫu nhiên một file ảnh (.jpg) hoặc video (.mp4).
     *
     * @return Đường dẫn tuyệt đối đến file nền, hoặc null nếu lỗi.
     */
    public String loadBackground() {
        try {
            logger.info("Đang tải tài nguyên nền từ: {}", backgroundPath);
            File backgroundDir = new File(backgroundPath);

            if (!backgroundDir.exists() || !backgroundDir.isDirectory()) {
                logger.error("❌ Thư mục nền không tồn tại hoặc không hợp lệ: {}", backgroundPath);
                return null;
            }

            File[] files = backgroundDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".mp4") || name.toLowerCase().endsWith(".jpg")
            );

            if (files == null || files.length == 0) {
                logger.error("❌ Không tìm thấy ảnh/video nền trong thư mục: {}", backgroundPath);
                return null;
            }

            File selectedFile = files[new Random().nextInt(files.length)];
            logger.info("✅ Đã chọn tài nguyên nền: {}", selectedFile.getName());
            return selectedFile.getAbsolutePath();

        } catch (Exception e) {
            logger.error("❌ Lỗi khi tải tài nguyên nền: {}", e.getMessage(), e);
            return null;
        }
    }
}
