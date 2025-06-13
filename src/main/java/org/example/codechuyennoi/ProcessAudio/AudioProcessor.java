package org.example.codechuyennoi.ProcessAudio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Lớp AudioProcessor chịu trách nhiệm xử lý file âm thanh đã được tạo từ văn bản.
 */
@Service
public class AudioProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AudioProcessor.class);

    /**
     * Phương thức processAudio kiểm tra và xử lý file âm thanh được truyền vào.
     * Hiện tại chưa có xử lý cụ thể nào, chỉ kiểm tra sự tồn tại và trả về đối tượng đầu vào.
     *
     * @param audio đối tượng AudioStory chứa thông tin file âm thanh cần xử lý
     * @return đối tượng AudioStory nếu xử lý thành công hoặc chưa cần xử lý, null nếu có lỗi
     */
    public AudioStory processAudio(AudioStory audio) {
        if (audio == null || audio.getAudioFilePath() == null) {
            logger.warn("Đối tượng AudioStory hoặc đường dẫn file bị null.");
            return null;
        }

        File audioFile = new File(audio.getAudioFilePath());
        if (!audioFile.exists()) {
            logger.warn("File không tồn tại tại đường dẫn: {}", audio.getAudioFilePath());
            return null;
        }

        try {
            logger.info("Đang xử lý âm thanh: {}", audio.getAudioFilePath());

            // Hiện tại chỉ trả về lại đối tượng mà không thực hiện thay đổi gì
            return audio;
        } catch (Exception e) {
            logger.error("Lỗi khi xử lý âm thanh: {}", e.getMessage(), e);
            return null;
        }
    }
}
