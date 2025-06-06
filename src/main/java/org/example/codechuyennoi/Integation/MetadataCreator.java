package org.example.codechuyennoi.Integation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataCreator {
    private static final Logger logger = LoggerFactory.getLogger(MetadataCreator.class);

    public String createMetadata(String videoTitle, String videoDescription) {
        try {
            logger.info("Đang tạo metadata cho video");
            return videoTitle + " - " + videoDescription;
        } catch (Exception e) {
            logger.error("Lỗi khi tạo metadata: {}", e.getMessage());
            return "Video kể chuyện tự động.";
        }
    }
}
