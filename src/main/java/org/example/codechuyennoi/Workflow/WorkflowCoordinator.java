package org.example.codechuyennoi.Workflow;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.example.codechuyennoi.Image.AiImageGenerator;
import org.example.codechuyennoi.Integation.MetadataCreator;
import org.example.codechuyennoi.Notification.ChapterMonitor;
import org.example.codechuyennoi.Notification.NotificationService;
import org.example.codechuyennoi.ProcessAudio.AudioGenerator;
import org.example.codechuyennoi.ProcessAudio.AudioProcessor;
import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.example.codechuyennoi.ProcessStory.StoryProcessor;
import org.example.codechuyennoi.ProcessVideo.VideoComposer;
import org.example.codechuyennoi.ProcessVideo.VideoMerger;
import org.example.codechuyennoi.ProcessVideo.VideoStory;
import org.example.codechuyennoi.Integation.YouTubeUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điều phối toàn bộ workflow tự động tạo video kể chuyện từ chương truyện:
 * 1. Quét chương mới
 * 2. Xử lý văn bản → âm thanh → ảnh → video
 * 3. Tải lên YouTube
 * 4. Gửi thông báo
 */
public class WorkflowCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowCoordinator.class);

    // Các thành phần chính xử lý trong pipeline
    private final StoryProcessor storyProcessor;
    private final AudioGenerator audioGenerator;
    private final AudioProcessor audioProcessor;
    private final AiImageGenerator aiImageGenerator;
    private final VideoComposer videoComposer;
    private final MetadataCreator metadataCreator;
    private final YouTubeUploader youTubeUploader;
    private final NotificationService notificationService;

    // Hàng đợi chương mới được phát hiện từ ChapterMonitor
    private final BlockingQueue<Integer> chapterQueue = new LinkedBlockingQueue<>();
    private Thread monitorThread;

    // Thông tin cấu hình cơ bản
    private final String baseUrl;     // URL base để tải chương
    private final String storyName;   // Tên truyện đang xử lý

     //Constructor khởi tạo toàn bộ thành phần và bắt đầu luồng quét chương mới
    public WorkflowCoordinator(Properties config, String storyName) {
        this.storyName = storyName;
        this.baseUrl = config.getProperty("story.base.url");

        // Lấy đường dẫn công cụ FFmpeg và thông tin OAuth YouTube
        String ffmpegPath = config.getProperty("ffmpeg.path");
        String ffprobePath = config.getProperty("ffprobe.path");
        String clientSecretPath = config.getProperty("google.oauth.client.secret.path");

        // Khởi tạo các thành phần xử lý
        this.storyProcessor = new StoryProcessor(config);
        this.audioGenerator = new AudioGenerator();
        this.audioProcessor = new AudioProcessor();
        this.aiImageGenerator = new AiImageGenerator(); // Có thể thay bằng AI thật sau này
        VideoMerger videoMerger = new VideoMerger(ffmpegPath);
        this.videoComposer = new VideoComposer(ffmpegPath, ffprobePath, videoMerger);
        this.metadataCreator = new MetadataCreator();
        this.youTubeUploader = new YouTubeUploader(clientSecretPath);
        this.notificationService = new NotificationService();

        // Khởi động tiến trình theo dõi chương mới và xử lý khi phát hiện
        startChapterMonitoring(config);
        processNewChapters();
    }

    //Xử lý batch chương từ start đến end (đã gồm cả audio, ảnh, video, upload).
    public void processMultipleChapters(int startChapter, int endChapter) {
        logger.info("Bắt đầu xử lý batch chương từ {} đến {} với base URL: {}", startChapter, endChapter, baseUrl);
        try {
            List<Story> processedStories = storyProcessor.processChaptersInBatch(storyName, baseUrl, startChapter, endChapter);
            for (Story story : processedStories) {
                // Bước 1: Chuyển văn bản thành audio (Text-to-Speech)
                AudioStory audioStory = generateAudio(story);
               // VideoStory videoStory = composeVideo(story, audioStory);
                //String youtubeId = uploadToYouTube(videoStory);
                //sendSuccessNotification(youtubeId);
            }
        } catch (Exception e) {
            logger.error("Lỗi nghiêm trọng trong quy trình batch: {}", e.getMessage(), e);
            notifyFailure("Lỗi hệ thống trong quy trình batch: " + e.getMessage());
        }
        logger.info("Hoàn thành xử lý batch chương.");
    }

     //Xử lý sinh âm thanh từ nội dung câu chuyện.
    private AudioStory generateAudio(Story story) {
        AudioStory audioStory = audioGenerator.generateAudio(storyName, story.getChapterNumber(), story.getProcessedText());
        return audioProcessor.processAudio(audioStory); // có thể cắt, ghép, xử lý file WAV/MP3
    }

     // Tạo video từ ảnh + audio + metadata.
     //Khi dùng AI thật, `aiImageGenerator.generateImages(...)` sẽ sinh ảnh thực.
     private VideoStory composeVideo(Story story, AudioStory audioStory) {
         List<String> prompts = story.getSentences(); // Câu mô tả ảnh
         List<String> imagePaths = aiImageGenerator.generateImages(prompts); // Dùng ảnh mẫu hoặc AI
         String title = "Chuyện cổ tích";
         String description = "Video kể chuyện tự động được tạo bởi hệ thống.";
         // Bước 1: Tạo video chương riêng như bình thường
         VideoStory chapterVideo = videoComposer.composeVideo(story, audioStory, imagePaths, title, description);
         if (chapterVideo == null) {
             logger.error("❌ Tạo video chương thất bại cho chương: {}", story.getChapterNumber());
             return null;
         }
         // Bước 2: Lấy folder chứa video các chương
         String storyFolder = "output/video_" + story.getStoryName();
         // Bước 3: Gộp video chương thành video tổng bằng hàm đã có log chi tiết
         String fullVideoPath = videoComposer.mergeChapterVideos(storyFolder);
         if (fullVideoPath == null) {
             logger.error("❌ Gộp video chương thất bại cho truyện: {}", story.getStoryName());
             return null;
         }
         // Bước 4: Trả về VideoStory đại diện cho video tổng
         return new VideoStory(fullVideoPath, title, description, null, audioStory, audioStory, null);
     }

    //Tải video lên YouTube.
    private String uploadToYouTube(VideoStory videoStory) throws IOException {
        String metadata = metadataCreator.createMetadata("Chuyện cổ tích", "Tự động tạo video kể chuyện.");
        return youTubeUploader.uploadVideo(videoStory, metadata);
    }

     //Gửi thông báo khi xử lý thành công một chương hoặc video.
    private void sendSuccessNotification(String youtubeId) {
        notificationService.sendCompletionNotification(true, "Quy trình hoàn thành, YouTube ID: " + youtubeId);
    }

     // Gửi thông báo khi xảy ra lỗi nghiêm trọng.
    private void notifyFailure(String message) {
        logger.error("Thông báo thất bại: {}", message);
        notificationService.sendCompletionNotification(false, message);
    }

     //Khởi động luồng theo dõi chương mới (giám sát file hoặc API).
    private void startChapterMonitoring(Properties config) {
        String lastChapterFilePath = config.getProperty("last.chapter.file", "lastChapter.txt");
        ChapterMonitor monitor = new ChapterMonitor(chapterQueue, baseUrl, lastChapterFilePath, storyName);
        monitorThread = new Thread(monitor);
        monitorThread.start();
        logger.info("ChapterMonitor đã được khởi động.");
    }

    //Luồng xử lý chương mới từ hàng đợi (được đẩy vào bởi ChapterMonitor).
    private void processNewChapters() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Integer chapter = chapterQueue.take(); // Đợi chương mới
                logger.info("Phát hiện chương mới: {}", chapter);
                processMultipleChapters(chapter, chapter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Cho phép dừng thread an toàn
            } catch (Exception e) {
                logger.error("Lỗi khi xử lý chương mới: {}", e.getMessage(), e);
                notifyFailure("Lỗi xử lý chương mới: " + e.getMessage());
            }
        }
    }
}
