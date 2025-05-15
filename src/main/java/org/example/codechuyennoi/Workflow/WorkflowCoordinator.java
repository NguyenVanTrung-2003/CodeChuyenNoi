package org.example.codechuyennoi.Workflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets; // Để đảm bảo encoding đúng

import org.example.codechuyennoi.Integation.MetadataCreator;
import org.example.codechuyennoi.Notification.NotificationService;
import org.example.codechuyennoi.ProcessAudio.AudioGenerator;
import org.example.codechuyennoi.ProcessAudio.AudioProcessor;
import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.example.codechuyennoi.ProcessStory.StoryProcessor;
import org.example.codechuyennoi.ProcessVideo.VideoComposer;
import org.example.codechuyennoi.ProcessVideo.VideoStory;
import org.example.codechuyennoi.Integation.YouTubeUploader;
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
    }
    public void processStory() {
        logger.info("Bắt đầu quy trình xử lý câu chuyện tổng thể.");
        try {
            Optional<String> maybeCleanedText = storyProcessor.processStoryFromSource();
            Story story = createStory(maybeCleanedText.get());
            AudioStory audioStory = generateAudio(story);
            VideoStory videoStory = composeVideo(story, audioStory);
            String youtubeId = uploadToYouTube(videoStory);
            sendSuccessNotification(youtubeId);
        } catch (Exception e) {
            logger.error("Lỗi nghiêm trọng trong quy trình tổng thể: {}", e.getMessage(), e);
            notifyFailure("Lỗi hệ thống trong quy trình tổng thể: " + e.getMessage());
        }
    }
    private Story createStory(String cleanedText) {
        return new Story(cleanedText);
    }

    private AudioStory generateAudio(Story story) {
        AudioStory audioStory = audioGenerator.generateAudio(story.getProcessedText());
        return audioProcessor.processAudio(audioStory);
    }

    private VideoStory composeVideo(Story story, AudioStory audioStory) {
        String backgroundPath = backgroundManager.loadBackground();
        String title = "Chuyện cổ tích";
        String description = "Video kể chuyện tự động được tạo bởi hệ thống.";
        return videoComposer.composeVideo(story, audioStory, backgroundPath, title, description);
    }

    private String uploadToYouTube(VideoStory videoStory) throws IOException {
        String metadata = metadataCreator.createMetadata("Chuyện cổ tích", "Tự động tạo video kể chuyện.");
        return youTubeUploader.uploadVideo(videoStory, metadata);
    }

    private void sendSuccessNotification(String youtubeId) {
        notificationService.sendCompletionNotification(true, "Quy trình hoàn thành, YouTube ID: " + youtubeId);
    }
    private void notifyFailure(String message) {
        logger.error("Thông báo thất bại: {}", message); // Ghi log thông báo thất bại
        notificationService.sendCompletionNotification(false, message);
    }

}