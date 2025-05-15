package org.example.codechuyennoi.ProcessStory;

import org.example.codechuyennoi.ProcessText.CleanText;
import org.example.codechuyennoi.ProcessText.ExtractorText;
import org.example.codechuyennoi.ProcessText.SourceStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class StoryProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StoryProcessor.class);

    private final SourceStory sourceStory;
    private final ExtractorText extractorText;
    private final CleanText cleanText;

    // Biến lưu bản truyện mới nhất đã xử lý (cache)
    private Story latestProcessedStory;

    public StoryProcessor(Properties config) {
        String urlSource = config.getProperty("story.url",
                "https://truyenfull.vision/loi-hua-khuynh-the-tuyet-mac/chuong-3/");
        this.sourceStory = new SourceStory(urlSource);
        this.extractorText = new ExtractorText();
        this.cleanText = new CleanText();
    }

    /**
     * Xử lý toàn bộ quy trình và lưu truyện ra file.
     */
    public Optional<Story> processAndCreateStory() {
        long start = System.currentTimeMillis();

        try {
            logger.info("Bắt đầu quy trình xử lý truyện...");

            // Bước 1: Tải HTML
            String htmlContent = sourceStory.fetchHtmlContent();
            if (htmlContent == null || htmlContent.isEmpty()) {
                logger.error("Không thể tải nội dung HTML từ nguồn.");
                return Optional.empty();
            }

            // Bước 2: Trích xuất văn bản
            String rawText = extractorText.extractText(htmlContent);
            if (rawText == null || rawText.isEmpty()) {
                logger.error("Không thể trích xuất văn bản từ HTML.");
                return Optional.empty();
            }

            // Bước 3: Làm sạch văn bản
            String cleanedText = cleanText.cleanStoryText(rawText);
            if (cleanedText == null || cleanedText.isEmpty()) {
                logger.error("Văn bản sau khi làm sạch bị rỗng.");
                return Optional.empty();
            }

            // Tạo đối tượng Story
            Story story = new Story(cleanedText);
            this.latestProcessedStory = story;

            // Lưu ra file
            saveStoryToFile(story, "luutrutruyen/clean_truyen.txt");

            logger.info("Hoàn tất xử lý truyện trong {}ms.", System.currentTimeMillis() - start);
            return Optional.of(story);

        } catch (Exception e) {
            logger.error("Đã xảy ra lỗi trong quá trình xử lý truyện: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Lưu văn bản truyện vào file.
     */
    private void saveStoryToFile(Story story, String filePath) {
        try {
            Files.createDirectories(Path.of(filePath).getParent());
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(story.getProcessedText());
                logger.info("Đã lưu truyện vào file: {}", filePath);
            }
        } catch (IOException e) {
            logger.error("Lỗi khi lưu truyện vào file: {}", e.getMessage(), e);
        }
    }

    /**
     * Trả về văn bản truyện nếu xử lý thành công, hoặc rỗng nếu thất bại.
     */
    public Optional<String> processStoryFromSource() {
        Optional<Story> maybeStory = processAndCreateStory();
        return maybeStory.map(Story::getProcessedText);
    }
}
