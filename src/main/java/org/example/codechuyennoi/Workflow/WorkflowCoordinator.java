package org.example.codechuyennoi.Workflow;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.example.codechuyennoi.Integation.MetadataCreator;
import org.example.codechuyennoi.Notification.ChapterMonitor;
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

public class WorkflowCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowCoordinator.class);

    private final StoryProcessor storyProcessor;
    private final AudioGenerator audioGenerator;
    private final AudioProcessor audioProcessor;
    private final BackgroundManager backgroundManager;
    private final VideoComposer videoComposer;
    private final MetadataCreator metadataCreator;
    private final YouTubeUploader youTubeUploader;
    private final NotificationService notificationService;
    private final BlockingQueue<Integer> chapterQueue = new LinkedBlockingQueue<>();
    private Thread monitorThread;

    private final String baseUrl;
    private final String storyName; //  Lưu lại tên truyện

    public WorkflowCoordinator(Properties config, String storyName) {
        this.storyName = storyName; // Lưu vào field
        this.baseUrl = config.getProperty("story.base.url");
        String ffmpegPath = config.getProperty("ffmpeg.path");
        String backgroundPath = config.getProperty("background.resources.path");
        String clientSecretPath = config.getProperty("google.oauth.client.secret.path");
        this.storyProcessor = new StoryProcessor(config);
        this.audioGenerator = new AudioGenerator();
        this.audioProcessor = new AudioProcessor();
        this.backgroundManager = new BackgroundManager(backgroundPath);
        this.videoComposer = new VideoComposer(ffmpegPath);
        this.metadataCreator = new MetadataCreator();
        this.youTubeUploader = new YouTubeUploader(clientSecretPath);
        this.notificationService = new NotificationService();
        startChapterMonitoring(config);
    }

    public void processMultipleChapters(String storyName, String baseUrl, int startChapter, int endChapter) {
        logger.info("Bắt đầu xử lý batch chương từ {} đến {} với base URL: {}", startChapter, endChapter, baseUrl);
        try {
            List<Story> processedStories = storyProcessor.processChaptersInBatch(storyName, baseUrl, startChapter, endChapter);

            for (Story story : processedStories) {
                AudioStory audioStory = generateAudio(story);
                // VideoStory videoStory = composeVideo(story, audioStory);
                // String youtubeId = uploadToYouTube(videoStory);
                // sendSuccessNotification(youtubeId);
            }
        } catch (Exception e) {
            logger.error("Lỗi nghiêm trọng trong quy trình batch: {}", e.getMessage(), e);
            notifyFailure("Lỗi hệ thống trong quy trình batch: " + e.getMessage());
        }
        logger.info("Hoàn thành xử lý batch chương.");
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
        logger.error("Thông báo thất bại: {}", message);
        notificationService.sendCompletionNotification(false, message);
    }
    private void startChapterMonitoring(Properties config) {
        String lastChapterFilePath = config.getProperty("last.chapter.file", "lastChapter.txt");
        // Giả sử bạn đã có biến storyName
        ChapterMonitor monitor = new ChapterMonitor(chapterQueue, baseUrl, lastChapterFilePath, storyName);
        monitorThread = new Thread(monitor);
        monitorThread.start();
        logger.info("ChapterMonitor đã được khởi động.");

    }

    private void processNewChapters() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Integer chapter = chapterQueue.take();
                logger.info("Phát hiện chương mới: {}", chapter);
                processMultipleChapters(storyName, baseUrl, chapter, chapter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Lỗi khi xử lý chương mới: {}", e.getMessage(), e);
                notifyFailure("Lỗi xử lý chương mới: " + e.getMessage());
            }
        }
    }
}
