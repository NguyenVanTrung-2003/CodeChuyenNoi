package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudioGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerator.class);
    private final TextToSpeech ttsWrapper;

    public AudioGenerator(TextToSpeech ttsWrapper) {
        this.ttsWrapper = ttsWrapper;
    }

    public AudioStory generateAudio(String storyName, int chapterNumber, String processedText) {
        if (processedText == null || processedText.isBlank()) {
            logger.warn("Văn bản đã xử lý rỗng cho truyện '{}', chương {}", storyName, chapterNumber);
            return null;
        }

        logger.info("Đang tạo âm thanh từ văn bản bằng API AISpeech cho truyện '{}', chương {}", storyName, chapterNumber);

        // Tạo thư mục đầu ra cho âm thanh
        File storyOutputDir = new File("output" + File.separator + storyName);
        if (!storyOutputDir.exists() && !storyOutputDir.mkdirs()) {
            logger.error("Không thể tạo thư mục đầu ra: {}", storyOutputDir.getAbsolutePath());
            return null;
        }

        // Tạo thư mục và lưu file phụ đề
        String normalizedStoryName = storyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .trim();
        String subtitlePath = String.format("luutrutruyen/%s/chuong_%d.txt", normalizedStoryName, chapterNumber);
        Path subtitleFilePath = Paths.get(subtitlePath).toAbsolutePath();
        try {
            Files.createDirectories(subtitleFilePath.getParent());
            logger.info("Đã tạo/kiểm tra thư mục phụ đề: {}", subtitleFilePath.getParent());
            // Chia văn bản thành các dòng
            String[] lines = processedText.split("\\r?\\n|\\.|\\!|\\?");
            List<String> subtitleLines = new ArrayList<>();
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    subtitleLines.add(line.trim());
                }
            }
            Files.write(subtitleFilePath, String.join("\n", subtitleLines).getBytes());
            logger.info("Đã lưu file phụ đề: {}, kích thước: {} bytes", subtitleFilePath, Files.size(subtitleFilePath));
        } catch (Exception e) {
            logger.error("Lỗi khi lưu file phụ đề {}: {}", subtitleFilePath, e.getMessage(), e);
            return null;
        }

        try {
            ByteString audioContent = ttsWrapper.synthesize(processedText);
            logger.info("Kích thước dữ liệu âm thanh: {} bytes", audioContent.size());

            String fileName = "audio_chuong_" + chapterNumber + ".mp3";
            File outputFile = new File(storyOutputDir, fileName);

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                out.write(audioContent.toByteArray());
            }

            logger.info("File âm thanh đã tạo: {}, kích thước: {} bytes", outputFile.getAbsolutePath(), Files.size(outputFile.toPath()));

            return new AudioStory(outputFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Lỗi khi tạo âm thanh: {}", e.getMessage(), e);
            return null;
        }
    }
}