package org.example.codechuyennoi.ProcessText;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Lớp ExtractorText chịu trách nhiệm trích xuất văn bản chính từ nội dung HTML thô.
 * Sử dụng thư viện Jsoup để phân tích cú pháp HTML và lấy đoạn văn bản chứa truyện.
 */
@Component
public class ExtractorText {
    private static final Logger logger = LoggerFactory.getLogger(ExtractorText.class);

    /** Phương thức extractText nhận vào nội dung HTML thô của một chương truyện,
     * trích xuất và trả về phần văn bản chính của chương đó.
     *
     * @param rawHtmlContent chuỗi HTML thô
     * @return văn bản truyện đã được làm sạch (không chứa thẻ HTML, khoảng trắng thừa),
     *         hoặc chuỗi rỗng nếu không trích xuất được
     */
    public String extractText(String rawHtmlContent) {
        // Kiểm tra đầu vào có hợp lệ không
        if (rawHtmlContent == null || rawHtmlContent.isEmpty()) {
            logger.warn("Nội dung HTML rỗng");
            return "";
        }
        try {
            logger.info("Đang trích xuất văn bản từ HTML...");
            // Phân tích cú pháp HTML thành Document
            Document doc = Jsoup.parse(rawHtmlContent);

            // Tìm phần tử HTML chính chứa nội dung truyện theo class (ví dụ: "chapter-c")
            // Nếu website khác có thể thay đổi selector này cho phù hợp
            Element chapterContent = doc.selectFirst(".chapter-c");

            if (chapterContent == null) {
                logger.error("Không tìm thấy thẻ chứa nội dung truyện (class='chapter-c')");
                return "";
            }
            // Lấy văn bản thuần từ phần tử đó, loại bỏ thẻ HTML bên trong
            String text = chapterContent.text();

            // Loại bỏ khoảng trắng thừa, xuống dòng dư thừa
            return text.replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            logger.error("Lỗi khi trích xuất văn bản: {}", e.getMessage(), e);
            return "";
        }
    }
}
