package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;

public class AudioGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerator.class);

    public AudioStory generateAudio(String storyName, int chapterNumber,String processedText) {
        if (processedText == null || processedText.isEmpty()) {
            logger.warn("Văn bản đã xử lý rỗng");
            return null;
        }

        logger.info("Đang tạo âm thanh từ văn bản bằng eSpeak cho truyện '{}'", storyName);

        // Tạo thư mục riêng cho truyện trong thư mục output
        File storyOutputDir = new File("output" + File.separator + storyName);
        if (!storyOutputDir.exists()) {
            boolean created = storyOutputDir.mkdirs();
            if (created) {
                logger.info("Đã tạo thư mục đầu ra: {}", storyOutputDir.getAbsolutePath());
            } else {
                logger.error("Không thể tạo thư mục đầu ra: {}", storyOutputDir.getAbsolutePath());
                return null;
            }
        }

        try (TextToSpeech ttsWrapper = new TextToSpeech("vi+f3", 150, 120)) {
            ByteString audioContent = ttsWrapper.synthesize(processedText);
            logger.info("Kích thước dữ liệu âm thanh: {} bytes", audioContent.size());

            String fileName = "audio_chuong_" + chapterNumber + ".wav";
            String outputPath = storyOutputDir.getPath() + File.separator + fileName;
            logger.info("Đường dẫn tuyệt đối file âm thanh: {}", new File(outputPath).getAbsolutePath());

            try (FileOutputStream out = new FileOutputStream(outputPath)) {
                out.write(audioContent.toByteArray());
            }

            File audioFile = new File(outputPath);
            if (audioFile.exists()) {
                logger.info("File âm thanh đã tạo, kích thước: {} bytes", audioFile.length());
            } else {
                logger.warn("File âm thanh không tồn tại sau khi ghi.");
            }

            return new AudioStory(outputPath);
        } catch (Exception e) {
            logger.error("Lỗi khi tạo âm thanh bằng eSpeak: {}", e.getMessage(), e);
            return null;
        }

    }
}
