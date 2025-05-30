package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public class AudioGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerator.class);

    /**
     * Tạo file âm thanh từ văn bản đã xử lý thông qua dịch vụ AISpeech TTS API.
     *
     * @param storyName     tên truyện
     * @param chapterNumber số chương
     * @param processedText văn bản đã xử lý
     * @return AudioStory nếu thành công, null nếu thất bại
     */
    public AudioStory generateAudio(String storyName, int chapterNumber, String processedText) {
        if (processedText == null || processedText.isBlank()) {
            logger.warn("Văn bản đã xử lý rỗng");
            return null;
        }

        logger.info("Đang tạo âm thanh từ văn bản bằng API AISpeech cho truyện '{}'", storyName);

        // Tạo thư mục đầu ra
        File storyOutputDir = new File("output" + File.separator + storyName);
        if (!storyOutputDir.exists() && !storyOutputDir.mkdirs()) {
            logger.error("Không thể tạo thư mục đầu ra: {}", storyOutputDir.getAbsolutePath());
            return null;
        }

        try {
            // Sử dụng lớp TextToSpeech để lấy dữ liệu âm thanh
            TextToSpeech ttsWrapper = new TextToSpeech();
            ByteString audioContent = ttsWrapper.synthesize(processedText);

            logger.info("Kích thước dữ liệu âm thanh: {} bytes", audioContent.size());

            // Tạo đường dẫn và ghi file
            String fileName = "audio_chuong_" + chapterNumber + ".mp3"; // File là mp3 chứ không phải wav
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
