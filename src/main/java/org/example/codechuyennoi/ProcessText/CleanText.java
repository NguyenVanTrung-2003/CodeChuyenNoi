package org.example.codechuyennoi.ProcessText;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CleanText {
    private static final Logger logger = LoggerFactory.getLogger(CleanText.class);

    public String cleanStoryText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            logger.warn("Văn bản thô rỗng");
            return "";
        }
        try {
            logger.info("Đang làm sạch văn bản");
            String text = rawText;
            text = removeAds(text);

            // Chỉ loại bỏ ký tự đặc biệt không cần thiết, giữ lại câu và ngắt dòng
            String cleanedText = text
                    .replaceAll("[^\\p{L}\\p{N}\\s.,!?\"“”‘’]", "")  // giữ lại câu và dấu hợp lệ
                    .replaceAll("\\s+", " ")                          // gom khoảng trắng
                    .replaceAll("(?m)^\\s*", "")                      // xóa khoảng trắng đầu dòng
                    .trim();
            return cleanedText;
        } catch (Exception e) {
            logger.error("Lỗi khi làm sạch văn bản: {}", e.getMessage());
            return "";
        }
    }

    private String removeAds(String text) {
        // Danh sách các mẫu quảng cáo phổ biến
        String[] adPatterns = {
                "🍊",
                "Đọc truyện tại[^\\n]*",           // ví dụ: Đọc truyện tại abc.xyz
                "Nhấn theo dõi[^\\n]*",            // ví dụ: Nhấn theo dõi để xem chương tiếp
                "Chương mới nhất tại[^\\n]*",
                "Truyện được đăng tải[^\\n]*",
                "Website chính[^\\n]*",
                "Theo dõi để cập nhật[^\\n]*",
                "Cập nhật sớm nhất tại[^\\n]*",
                "Fanpage[^\\n]*",
                "Donate[^\\n]*"
        };

        for (String pattern : adPatterns) {
            text = text.replaceAll("(?i)" + pattern, "");  // (?i) để không phân biệt hoa thường
        }
        return text;
    }
}
