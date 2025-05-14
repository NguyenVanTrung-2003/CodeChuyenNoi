package org.example.codechuyennoi.ProcessStory;

import org.example.codechuyennoi.ProcessText.CleanText;
import org.example.codechuyennoi.ProcessText.ExtractorText;
import org.example.codechuyennoi.ProcessText.SourceStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;

public class StoryProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StoryProcessor.class);
    private final SourceStory sourceStory;
    private final ExtractorText extractorText;
    private final CleanText cleanText;

    // Sửa trong StoryProcessor.java
    public StoryProcessor(Properties config) {
        String urlSource = config.getProperty("story.url", "https://truyenfull.vision/loi-hua-khuynh-the-tuyet-mac/chuong-2/");
        this.sourceStory = new SourceStory(urlSource);
        this.extractorText = new ExtractorText();
        this.cleanText = new CleanText();
    }

    public Optional<String> processStoryFromSource() {
        long start = System.currentTimeMillis();
        try {
            logger.info("Bắt đầu quy trình xử lý truyện...");

            // Bước 1: Tải HTML từ nguồn
            String htmlContent = sourceStory.fetchHtmlContent();
            if (htmlContent == null || htmlContent.isEmpty()) {
                logger.error("Không thể tải nội dung HTML từ nguồn.");
                return Optional.empty();
            }
            logger.info("Đã tải xong HTML.");

            // Bước 2: Trích xuất văn bản thô từ HTML
            String rawText = extractorText.extractText(htmlContent);
            if (rawText == null || rawText.isEmpty()) {
                logger.error("Không thể trích xuất văn bản từ HTML.");
                return Optional.empty();
            }
            logger.info("Đã trích xuất văn bản thô.");

            // Bước 3: Làm sạch văn bản
            String cleanedText = cleanText.cleanStoryText(rawText);
            if (cleanedText == null || cleanedText.isEmpty()) {
                logger.error("Văn bản sau khi làm sạch bị rỗng.");
                return Optional.empty();
            }
            logger.info("Hoàn tất xử lý truyện trong {}ms.", System.currentTimeMillis() - start);
            return Optional.of(cleanedText);

        } catch (Exception e) {
            logger.error("Đã xảy ra lỗi trong quá trình xử lý truyện: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
