

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
import java.util.*;
import java.util.concurrent.*;

public class StoryProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StoryProcessor.class);

    private final ExtractorText extractorText;
    private final CleanText cleanText;
    private final int batchSize;
    private final Path storageDir = Path.of("luutrutruyen");

    public StoryProcessor(Properties config) {
        this.batchSize = Integer.parseInt(config.getProperty("story.batch.size", "5"));
        this.extractorText = new ExtractorText();
        this.cleanText = new CleanText();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            logger.warn("Không thể tạo thư mục lưu trữ gốc: {}", e.getMessage());
        }
    }

    /**
     * processChaptersInBatch: Xử lý batch chương từ start đến end
     * Lưu file chương theo thư mục riêng của truyện (storyName)
     */
    public List<Story> processChaptersInBatch(String storyName, String baseUrl, int startChapter, int endChapter) {
        logger.info("Bắt đầu xử lý truyện '{}' chương từ {} đến {}.", storyName,baseUrl, startChapter, endChapter);

        Path storyDir = storageDir.resolve(storyName);
        try {
            Files.createDirectories(storyDir);
        } catch (IOException e) {
            logger.warn("Không thể tạo thư mục truyện {}: {}", storyName, e.getMessage());
        }

        BlockingQueue<Integer> chapterQueue = new LinkedBlockingQueue<>();
        List<Story> processedStories = Collections.synchronizedList(new ArrayList<>());

        // Producer: quét & enqueue chương
        Thread producer = new Thread(() -> {
            for (int chap = startChapter; chap <= endChapter; chap++) {
                Path file = storyDir.resolve("chuong-" + chap + ".txt");
                if (Files.exists(file)) {
                    logger.info("Producer: chương {} đã có file, bỏ qua.", chap);
                    continue;
                }
                String url = baseUrl + "/chuong-" + chap + "/";
                String html;
                String rawText;
                try {
                    // 1) Tải HTML
                    html = new SourceStory(url).fetchHtmlContent();
                    if (html == null || html.isEmpty()) {
                        logger.info("Producer: chương {} không tồn tại (HTML rỗng). Dừng producer.", chap);
                        break;
                    }
                    // 2) Dùng extractorText để phát hiện nội dung
                    rawText = extractorText.extractText(html);
                    if (rawText == null || rawText.isEmpty()) {
                        logger.info("Producer: chương {} chưa tồn tại trên Web. kết thúc producer.", chap);
                        break;
                    }
                    // 3) Nếu có rawText, enqueue chương
                    chapterQueue.put(chap);
                    logger.info("Producer: enqueue chương {}.", chap);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Producer: lỗi kiểm tra chương {}: {}", chap, e.getMessage());
                    break;
                }
            }
        });
        producer.start();

        // Consumer pool: đa luồng xử lý chương
        ExecutorService consumers = Executors.newFixedThreadPool(batchSize);
        for (int i = 0; i < batchSize; i++) {
            consumers.submit(() -> {
                try {
                    while (true) {
                        Integer chap = chapterQueue.poll(3, TimeUnit.SECONDS);
                        if (chap == null) {
                            if (!producer.isAlive()) break;
                            else continue;
                        }
                        Optional<Story> storyOpt = processSingleChapter(storyName, baseUrl + "/chuong-" + chap + "/", chap);

                        storyOpt.ifPresent(processedStories::add);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        consumers.shutdown();
        try {
            consumers.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Hoàn thành xử lý truyện '{}'. Tổng chương thành công: {}", storyName, processedStories.size());
        return processedStories;
    }

    /**
     * Xử lý fetch/extract/clean và lưu file cho 1 chương cụ thể,
     * lưu file chương trong thư mục riêng của truyện.
     */
    public Optional<Story> processSingleChapter(String storyName, String chapterUrl, int chapterNumber) {
        Path chapterFile = storageDir.resolve(storyName).resolve("chuong-" + chapterNumber + ".txt");
        if (chapterFileExists(chapterFile)) {
            logger.info("Consumer: chương {} đã có file, bỏ qua (kiểm tra lần 2).", chapterNumber);
            return Optional.empty();
        }
        try {
            logger.info("Consumer: xử lý chương {}: {}", chapterNumber, chapterUrl);

            Optional<String> htmlContentOpt = fetchHtmlContentSafe(chapterUrl, chapterNumber);
            if (htmlContentOpt.isEmpty()) return Optional.empty();

            Optional<String> rawTextOpt = extractTextSafe(htmlContentOpt.get(), chapterNumber);
            if (rawTextOpt.isEmpty()) return Optional.empty();

            Optional<String> cleanedOpt = cleanTextSafe(rawTextOpt.get(), chapterNumber);
            if (cleanedOpt.isEmpty()) return Optional.empty();

            Story story = new Story(storyName,chapterNumber, cleanedOpt.get());
            saveStory(story, storyName, chapterNumber);

            return Optional.of(story);
        } catch (Exception e) {
            logger.error("Consumer: Lỗi xử lý chương {}: {}", chapterNumber, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private boolean chapterFileExists(Path chapterFile) {
        return Files.exists(chapterFile);
    }

    private Optional<String> fetchHtmlContentSafe(String url, int chapterNumber) {
        String content = new SourceStory(url).fetchHtmlContent();
        if (content == null || content.isEmpty()) {
            logger.warn("Consumer: không tải được HTML cho chương {}", chapterNumber);
            return Optional.empty();
        }
        return Optional.of(content);
    }

    private Optional<String> extractTextSafe(String html, int chapterNumber) {
        String rawText = extractorText.extractText(html);
        if (rawText == null || rawText.isEmpty()) {
            logger.warn("Consumer: không trích xuất được nội dung chương {}", chapterNumber);
            return Optional.empty();
        }
        return Optional.of(rawText);
    }

    private Optional<String> cleanTextSafe(String rawText, int chapterNumber) {
        String cleaned = cleanText.cleanStoryText(rawText);
        if (cleaned == null || cleaned.isEmpty()) {
            logger.warn("Consumer: nội dung rỗng sau clean chương {}", chapterNumber);
            return Optional.empty();
        }
        return Optional.of(cleaned);
    }

    private void saveStory(Story story, String storyName, int chapterNumber) throws IOException {
        Path storyDir = storageDir.resolve(storyName);
        String filePath = storyDir.resolve("chuong-" + chapterNumber + ".txt").toString();
        saveStoryToFile(story, filePath);
        logger.info("Consumer: Đã lưu chương {} vào file.", chapterNumber);
    }


    private void saveStoryToFile(Story story, String filePath) {
        try {
            Files.createDirectories(Path.of(filePath).getParent());
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(story.getProcessedText());
            }
        } catch (IOException e) {
            logger.error("Lỗi lưu file {}: {}", filePath, e.getMessage(), e);
        }
    }
}
