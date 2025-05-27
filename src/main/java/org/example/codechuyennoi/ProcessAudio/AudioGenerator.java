package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Lớp AudioGenerator dùng để chuyển văn bản đã xử lý thành file âm thanh bằng thư viện TextToSpeech (dùng eSpeak).
 */
public class AudioGenerator {
    // Logger dùng để ghi log thông tin trong quá trình thực thi
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerator.class);

    /**
     * Phương thức generateAudio nhận vào tên truyện, số chương và nội dung văn bản đã xử lý.
     * Sau đó chuyển văn bản thành file âm thanh .wav và lưu vào thư mục tương ứng với tên truyện.
     *
     * @param storyName     tên truyện
     * @param chapterNumber số chương
     * @param processedText văn bản đã xử lý
     * @return AudioStory chứa đường dẫn file âm thanh nếu thành công, null nếu thất bại
     */
    public AudioStory generateAudio(String storyName, int chapterNumber, String processedText) {
        // Kiểm tra văn bản đầu vào có hợp lệ hay không
        if (processedText == null || processedText.isEmpty()) {
            logger.warn("Văn bản đã xử lý rỗng");
            return null;
        }

        logger.info("Đang tạo âm thanh từ văn bản bằng eSpeak cho truyện '{}'", storyName);

        // Tạo thư mục đầu ra riêng cho từng truyện (ví dụ: output/TenTruyen)
        File storyOutputDir = new File("output" + File.separator + storyName);
        if (!storyOutputDir.exists()) {
            boolean created = storyOutputDir.mkdirs(); // Tạo thư mục nếu chưa tồn tại
            if (created) {
                logger.info("Đã tạo thư mục đầu ra: {}", storyOutputDir.getAbsolutePath());
            } else {
                logger.error("Không thể tạo thư mục đầu ra: {}", storyOutputDir.getAbsolutePath());
                return null;
            }
        }

        // Sử dụng TextToSpeech wrapper (dựa trên eSpeak) để tạo âm thanh
        try (TextToSpeech ttsWrapper = new TextToSpeech("vi+f3", 150, 120)) {
            // Chuyển văn bản thành dữ liệu âm thanh dưới dạng ByteString
            ByteString audioContent = ttsWrapper.synthesize(processedText);
            logger.info("Kích thước dữ liệu âm thanh: {} bytes", audioContent.size());

            // Tạo tên và đường dẫn cho file âm thanh đầu ra
            String fileName = "audio_chuong_" + chapterNumber + ".wav";
            String outputPath = storyOutputDir.getPath() + File.separator + fileName;
            logger.info("Đường dẫn tuyệt đối file âm thanh: {}", new File(outputPath).getAbsolutePath());

            // Ghi dữ liệu âm thanh vào file
            try (FileOutputStream out = new FileOutputStream(outputPath)) {
                out.write(audioContent.toByteArray());
            }

            // Kiểm tra xem file âm thanh đã được tạo thành công chưa
            File audioFile = new File(outputPath);
            if (audioFile.exists()) {
                logger.info("File âm thanh đã tạo, kích thước: {} bytes", audioFile.length());
            } else {
                logger.warn("File âm thanh không tồn tại sau khi ghi.");
            }

            // Trả về đối tượng AudioStory chứa thông tin file
            return new AudioStory(outputPath);
        } catch (Exception e) {
            logger.error("Lỗi khi tạo âm thanh bằng eSpeak: {}", e.getMessage(), e);
            return null;
        }
    }
}
