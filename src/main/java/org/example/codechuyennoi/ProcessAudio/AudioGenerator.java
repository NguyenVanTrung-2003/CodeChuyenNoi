package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;

public class AudioGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerator.class);
    public AudioStory generateAudio(String processedText) {
        if (processedText == null || processedText.isEmpty()) {
            logger.warn("Văn bản đã xử lý rỗng");
            return null;
        }
        logger.info("Đang tạo âm thanh từ văn bản bằng eSpeak");
        // Tạo thư mục output nếu chưa tồn tại
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (created) {
                logger.info("Đã tạo thư mục đầu ra: {}", outputDir.getAbsolutePath());
            } else {
                logger.error("Không thể tạo thư mục đầu ra: {}", outputDir.getAbsolutePath());
                return null;
            }
        }

        try (TextToSpeech ttsWrapper = new TextToSpeech("vi+f3", 150, 120)) {
            ByteString audioContent = ttsWrapper.synthesize(processedText);
            String outputPath = outputDir.getPath()
                    + File.separator
                    + "audio_" + System.currentTimeMillis() + ".wav";

            // Ghi ByteString vào file WAV
            try (FileOutputStream out = new FileOutputStream(outputPath)) {
                audioContent.writeTo(out);
            }
            logger.info("Đã tạo file âm thanh tại: {}", outputPath);
            return new AudioStory(outputPath);
        } catch (Exception e) {
            logger.error("Lỗi khi tạo âm thanh bằng eSpeak: {}", e.getMessage(), e);
            return null;
        }
    }
}
