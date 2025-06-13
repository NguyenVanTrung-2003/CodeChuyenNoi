

package org.example.codechuyennoi.ProcessStory;

import org.example.codechuyennoi.ProcessStory.Story;
import org.example.codechuyennoi.ProcessText.CleanText;
import org.example.codechuyennoi.ProcessText.ExtractorText;
import org.example.codechuyennoi.ProcessText.SourceStory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class StoryProcessor {

    private final ExtractorText extractorText;
    private final CleanText cleanText;

    @Value("${story.batch.size:5}")
    private int batchSize;

    @Value("${story.storage.path:luutrutruyen}")
    private String storagePath;

    private Path storageDir;

    public StoryProcessor(ExtractorText extractorText, CleanText cleanText) {
        this.extractorText = extractorText;
        this.cleanText = cleanText;
    }

    @PostConstruct
    public void init() {
        this.storageDir = Path.of(storagePath);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.warn("Không thể tạo thư mục lưu trữ: {}", e.getMessage());
        }
    }

    public List<Story> processChaptersInBatch(String storyName, String baseUrl, int startChapter, int endChapter) {
        log.info("Bắt đầu xử lý truyện '{}' từ chương {} đến {}", storyName, startChapter, endChapter);
        Path storyDir = storageDir.resolve(storyName);
        try {
            Files.createDirectories(storyDir);
        } catch (IOException e) {
            log.warn("Không thể tạo thư mục truyện {}: {}", storyName, e.getMessage());
        }

        BlockingQueue<Integer> chapterQueue = new LinkedBlockingQueue<>();
        List<Story> processedStories = Collections.synchronizedList(new ArrayList<>());

        Thread producer = new Thread(() -> {
            for (int chap = startChapter; chap <= endChapter; chap++) {
                Path file = storyDir.resolve("chuong-" + chap + ".txt");
                if (Files.exists(file)) {
                    log.info("Producer: chương {} đã có file, bỏ qua.", chap);
                    continue;
                }
                String url = baseUrl + "/chuong-" + chap + "/";
                try {
                    String html = new SourceStory(url).fetchHtmlContent();
                    if (html == null || html.isEmpty()) break;

                    String rawText = extractorText.extractText(html);
                    if (rawText == null || rawText.isEmpty()) break;

                    chapterQueue.put(chap);
                    log.info("Producer: enqueue chương {}.", chap);
                } catch (Exception e) {
                    log.error("Lỗi producer chương {}: {}", chap, e.getMessage());
                    break;
                }
            }
        });
        producer.start();

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

        log.info("Hoàn thành xử lý '{}'. Tổng số chương: {}", storyName, processedStories.size());
        return processedStories;
    }

    public Optional<Story> processSingleChapter(String storyName, String chapterUrl, int chapterNumber) {
        Path chapterFile = storageDir.resolve(storyName).resolve("chuong-" + chapterNumber + ".txt");
        if (Files.exists(chapterFile)) {
            log.info("Consumer: chương {} đã có file.", chapterNumber);
            return Optional.empty();
        }

        try {
            String html = new SourceStory(chapterUrl).fetchHtmlContent();
            if (html == null || html.isEmpty()) return Optional.empty();

            String rawText = extractorText.extractText(html);
            if (rawText == null || rawText.isEmpty()) return Optional.empty();

            String cleaned = cleanText.cleanStoryText(rawText);
            if (cleaned == null || cleaned.isEmpty()) return Optional.empty();

            Story story = new Story(storyName, chapterNumber, cleaned);
            saveStoryToFile(story, chapterFile);
            return Optional.of(story);
        } catch (Exception e) {
            log.error("Lỗi xử lý chương {}: {}", chapterNumber, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void saveStoryToFile(Story story, Path filePath) {
        try {
            Files.createDirectories(filePath.getParent());
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(story.getProcessedText());
            }
        } catch (IOException e) {
            log.error("Lỗi lưu chương: {}", e.getMessage(), e);
        }
    }
}

