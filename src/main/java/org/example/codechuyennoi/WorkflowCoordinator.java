package org.example.codechuyennoi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets; // Để đảm bảo encoding đúng
import org.example.codechuyennoi.AudioStory;
import org.example.codechuyennoi.Story;
import org.example.codechuyennoi.VideoStory;
import org.example.codechuyennoi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;

public class WorkflowCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowCoordinator.class);

    // Thay thế SourceStory, ExtractorText, CleanText bằng StoryProcessor
    private final StoryProcessor storyProcessor; // <-- Sử dụng lớp mới này

    private final AudioGenerator audioGenerator;
    private final AudioProcessor audioProcessor;
    private final BackgroundManager backgroundManager;
    private final VideoComposer videoComposer;
    private final MetadataCreator metadataCreator;
    private final YouTubeUploader youTubeUploader;
    private final NotificationService notificationService;

    public WorkflowCoordinator(Properties config) {
        // WorkflowCoordinator vẫn chịu trách nhiệm đọc cấu hình và khởi tạo các thành phần chính
        String ffmpegPath = config.getProperty("ffmpeg.path");
        String backgroundPath = config.getProperty("background.resources.path");
        String clientSecretPath = config.getProperty("google.oauth.client.secret.path");

        // Khởi tạo StoryProcessor và truyền cấu hình vào
        // StoryProcessor sẽ tự khởi tạo SourceStory bên trong nó
        this.storyProcessor = new StoryProcessor(config); // <-- Khởi tạo StoryProcessor mới

        // Khởi tạo các thành phần còn lại
        this.audioGenerator = new AudioGenerator();
        this.audioProcessor = new AudioProcessor();
        this.backgroundManager = new BackgroundManager(backgroundPath);
        this.videoComposer = new VideoComposer(ffmpegPath);
        this.metadataCreator = new MetadataCreator();
        this.youTubeUploader = new YouTubeUploader(clientSecretPath);
        this.notificationService = new NotificationService();

        logger.info("WorkflowCoordinator đã được khởi tạo.");
    }

    public void processStory() {
        logger.info("Bắt đầu quy trình xử lý câu chuyện tổng thể.");
        try {
            // Bước 1: Xử lý câu chuyện văn bản (sử dụng StoryProcessor để điều phối Source, Extract, Clean)
            logger.info("Bắt đầu giai đoạn xử lý văn bản nguồn...");
            // Gọi phương thức mới trong StoryProcessor để thực hiện toàn bộ bước này
            Optional<String> maybeCleanedText = storyProcessor.processStoryFromSource();
            if (maybeCleanedText.isEmpty()) {
                notifyFailure("Không thể xử lý văn bản.");
                return;
            }
            String cleanedText = maybeCleanedText.get();

            // Kiểm tra kết quả trả về từ StoryProcessor
            if (cleanedText == null || cleanedText.isEmpty()) {
                // StoryProcessor đã log lỗi cụ thể hơn bên trong processSourceText()
                notifyFailure("Không thể hoàn thành giai đoạn xử lý văn bản nguồn.");
                // Không cần return ở đây vì notifyFailure đã được gọi, luồng sẽ tự kết thúc qua try-catch hoặc logic tiếp theo
                return;
            }

            // Tiếp tục với văn bản đã làm sạch
            Story story = new Story(cleanedText);
            logger.info("Giai đoạn xử lý văn bản nguồn hoàn tất.");

            // --- Đoạn mã lưu file (Giữ nguyên ở đây vì WorkflowCoordinator điều phối các hành động ngoại vi) ---
            String outputFileName = "cleaned_story.txt";
            try {
                Files.write(Paths.get(outputFileName), cleanedText.getBytes(StandardCharsets.UTF_8));
                logger.info("Đã lưu văn bản đã làm sạch vào file: {}", outputFileName);
            } catch (IOException e) {
                logger.error("Lỗi khi lưu văn bản đã làm sạch vào file {}: {}", outputFileName, e.getMessage());
                // Chọn cách xử lý lỗi lưu file: dừng hoặc tiếp tục?
                // Hiện tại là tiếp tục, nếu muốn dừng thì thêm notifyFailure và return
                // notifyFailure("Lỗi hệ thống: Không thể lưu văn bản đã làm sạch.");
                // return;
            }
            // -------------------------------------------------------

            // Bước 2: Tạo âm thanh từ văn bản
            logger.info("Bắt đầu giai đoạn tạo âm thanh...");
            AudioStory audioStory = audioGenerator.generateAudio(story.getProcessedText());
            if (audioStory == null) {
                notifyFailure("Không thể tạo âm thanh");
                return;
            }
            audioStory = audioProcessor.processAudio(audioStory);
            if (audioStory == null) {
                notifyFailure("Không thể xử lý âm thanh");
                return;
            }
            logger.info("Giai đoạn tạo âm thanh hoàn tất.");

            // Bước 3: Tạo video từ âm thanh + văn bản
            logger.info("Bắt đầu giai đoạn tạo video...");
            String backgroundPath = backgroundManager.loadBackground();
            if (backgroundPath == null) {
                notifyFailure("Không thể tải tài nguyên nền");
                return;
            }
            VideoStory videoStory = videoComposer.composeVideo(story, audioStory, backgroundPath);
            if (videoStory == null) {
                notifyFailure("Không thể tổng hợp video");
                return;
            }
            String metadata = metadataCreator.createMetadata("Auto-generated Story", "This is an auto-generated story video.");
            if (metadata == null) {
                notifyFailure("Không thể tạo metadata");
                return;
            }
            logger.info("Giai đoạn tạo video hoàn tất.");

            // Bước 4: Tải lên YouTube
            logger.info("Bắt đầu giai đoạn tải lên YouTube...");
            String youtubeId = youTubeUploader.uploadVideo(videoStory, metadata);
            if (youtubeId == null) {
                notifyFailure("Không thể tải video lên YouTube");
                return;
            }
            logger.info("Giai đoạn tải lên YouTube hoàn tất.");

            // Bước 5: Thông báo kết quả
            logger.info("Quy trình tổng thể hoàn thành.");
            notificationService.sendCompletionNotification(true, "Quy trình hoàn thành, YouTube ID: " + youtubeId);

        } catch (Exception e) {
            // Bắt bất kỳ lỗi ngoại lệ nào xảy ra trong các bước
            logger.error("Lỗi nghiêm trọng trong quy trình tổng thể: {}", e.getMessage(), e); // Ghi log lỗi chi tiết hơn
            notifyFailure("Lỗi hệ thống trong quy trình tổng thể: " + e.getMessage()); // Cập nhật thông báo lỗi
        }
    }

    private void notifyFailure(String message) {
        logger.error("Thông báo thất bại: {}", message); // Ghi log thông báo thất bại
        notificationService.sendCompletionNotification(false, message);
    }

    // Giữ nguyên phương thức main để chạy chương trình
    public static void main(String[] args) {
        Properties config = new Properties();
        try {
            config.load(WorkflowCoordinator.class.getClassLoader().getResourceAsStream("application.properties"));
            logger.info("Đã tải cấu hình từ application.properties.");
        } catch (Exception e) {
            logger.error("Không thể tải cấu hình từ application.properties: {}", e.getMessage(), e);
            return;
        }
        WorkflowCoordinator coordinator = new WorkflowCoordinator(config);
        coordinator.processStory();
    }
}
