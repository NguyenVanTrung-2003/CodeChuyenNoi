package org.example.codechuyennoi.ProcessText;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractorText {
    private static final Logger logger = LoggerFactory.getLogger(ExtractorText.class);

    public String extractText(String rawHtmlContent) {
        if (rawHtmlContent == null || rawHtmlContent.isEmpty()) {
            logger.warn("Nội dung HTML rỗng");
            return "";
        }
        try {
            logger.info("Đang trích xuất văn bản từ HTML...");
            Document doc = Jsoup.parse(rawHtmlContent);

            // 👉 CHỈ lấy nội dung chính (văn bản truyện)
            Element chapterContent = doc.selectFirst(".chapter-c"); // hoặc ".chapter-content", tuỳ website

            if (chapterContent == null) {
                logger.error("Không tìm thấy thẻ chứa nội dung truyện (class='chapter-c')");
                return "";
            }

            String text = chapterContent.text();
            return text.replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            logger.error("Lỗi khi trích xuất văn bản: {}", e.getMessage(), e);
            return "";
        }
    }
}
