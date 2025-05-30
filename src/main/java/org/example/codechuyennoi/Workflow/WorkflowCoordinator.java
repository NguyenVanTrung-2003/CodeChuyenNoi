package org.example.codechuyennoi.Workflow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final VideoMerger videoMerger;

    // Hàng đợi chương mới được phát hiện từ ChapterMonitor
    private final BlockingQueue<Integer> chapterQueue = new LinkedBlockingQueue<>();
    private Thread monitorThread;

    // Thông tin cấu hình cơ bản
    private final String baseUrl;     // URL base để tải chương
    private final String storyName;   // Tên truyện đang xử lý

    // Constructor khởi tạo toàn bộ thành phần và bắt đầu luồng quét chương mới
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
        this.aiImageGenerator = new AiImageGenerator();
        this.videoMerger = new VideoMerger(ffmpegPath);
        this.videoComposer = new VideoComposer(ffmpegPath, ffprobePath);
        this.metadataCreator = new MetadataCreator();
        this.youTubeUploader = new YouTubeUploader(clientSecretPath);
        this.notificationService = new NotificationService();

        // Khởi động tiến trình theo dõi chương mới và xử lý khi phát hiện
        startChapterMonitoring(config);
        processNewChapters();
    }

    // Xử lý batch chương từ start đến end và chỉ tải video tổng hợp cuối cùng
    public void processMultipleChapters(int startChapter, int endChapter) {
        logger.info("Bắt đầu xử lý batch chương từ {} đến {} với base URL: {}", startChapter, endChapter, baseUrl);
        try {
            List<Story> processedStories = storyProcessor.processChaptersInBatch(storyName, baseUrl, startChapter, endChapter);

            for (Story story : processedStories) {
               AudioStory audioStory = generateAudio(story);//tạo audio
               VideoStory chapterVideo = composeVideo(story, audioStory);//tạo video từng chương
            }

            // Gộp và tải lên chỉ khi có nhiều hơn một chương
            int numberOfChapters = endChapter - startChapter + 1;
            if (numberOfChapters <= 1) {
                logger.warn("⚠️ Chỉ có {} chương được xử lý, bỏ qua bước gộp và upload YouTube.", numberOfChapters);
                return;
            }
            // Gộp video thành  một video full chưa tất cả chương
            VideoStory fullVideo = mergeAllChapterVideos(storyName, startChapter, endChapter);
            String youtubeId = uploadToYouTube(fullVideo);//upload lên yt
            sendSuccessNotification(youtubeId);

        } catch (Exception e) {
            logger.error("Lỗi nghiêm trọng trong quy trình batch: {}", e.getMessage(), e);
            notifyFailure("Lỗi hệ thống trong quy trình batch: " + e.getMessage());
        }
        logger.info("Hoàn thành xử lý batch chương.");
    }

    // Xử lý sinh âm thanh từ nội dung câu chuyện.
    private AudioStory generateAudio(Story story) {
        AudioStory audioStory = audioGenerator.generateAudio(storyName, story.getChapterNumber(), story.getProcessedText());
        return audioProcessor.processAudio(audioStory);
    }

    // Tạo video từ ảnh + audio + metadata.
    private VideoStory composeVideo(Story story, AudioStory audioStory) {
        List<String> prompts = story.getSentences();
        List<String> imagePaths = aiImageGenerator.generateImages(prompts);
        String title = "Chuyện cổ tích - Chương " + story.getChapterNumber();
        String description = "A Phiêu Cô Nương - Chương " + story.getChapterNumber();

        VideoStory chapterVideo = videoComposer.composeVideo(story, audioStory, imagePaths, title, description);
        if (chapterVideo == null) {
            logger.error("❌ Tạo video chương thất bại cho chương số: {}", story.getChapterNumber());
            return null;
        }
        logger.info("✅ Đã tạo video chương {}: {}", story.getChapterNumber(), chapterVideo.getDescription());
        return chapterVideo;
    }

    private VideoStory mergeAllChapterVideos(String storyName, int startChapter, int endChapter) {
        String storyFolderName = "video_" + storyName.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        String storyFolder = "output/" + storyFolderName;

        File folder = new File(storyFolder);
        if (!folder.exists() || !folder.isDirectory()) {
            logger.error("❌ Thư mục chứa video chương không tồn tại: {}", storyFolder);
            return null;
        }

        File[] videoFiles = folder.listFiles((dir, name) -> name.matches("video_chuong_\\d+\\.mp4"));
        if (videoFiles == null || videoFiles.length == 0) {
            logger.error("❌ Không tìm thấy file video chương để gộp trong thư mục: {}", storyFolder);
            return null;
        }

        logger.info("🔄 Gộp {} video chương trong thư mục: {}", videoFiles.length, storyFolder);
        String fullVideoPath = videoMerger.mergeVideos(storyFolder);
        if (fullVideoPath == null) {
            logger.error("❌ Gộp video chương thất bại.");
            return null;
        }

        logger.info("✅ Đã gộp video tổng hợp: {}", fullVideoPath);
        String title = "Chuyện cổ tích - " + storyName + " (Chương " + startChapter + (startChapter == endChapter ? "" : "-" + endChapter) + ")";
        String description = "A Phiêu Cô Nương - Truyện từ chương " + startChapter + (startChapter == endChapter ? "" : " đến " + endChapter) + ".";
        return new VideoStory(fullVideoPath, title, description, null, null, null, null);
    }

    // Tải video lên YouTube.
    private String uploadToYouTube(VideoStory videoStory) throws IOException {
        String metadata = metadataCreator.createMetadata(videoStory.getTitle(), videoStory.getDescription());
        return youTubeUploader.uploadVideo(videoStory, metadata);
    }

    // Gửi thông báo khi xử lý thành công một chương hoặc video.
    private void sendSuccessNotification(String youtubeId) {
        if (youtubeId != null) {
            notificationService.sendCompletionNotification(true, "Quy trình hoàn thành, YouTube ID: " + youtubeId);
        } else {
            logger.warn("Không gửi thông báo thành công vì YouTube ID là null");
        }
    }

    // Gửi thông báo khi xảy ra lỗi nghiêm trọng.
    private void notifyFailure(String message) {
        logger.error("Thông báo thất bại: {}", message);
        notificationService.sendCompletionNotification(false, message);
    }

    // Khởi động luồng theo dõi chương mới (giám sát file hoặc API).
    private void startChapterMonitoring(Properties config) {
        String lastChapterFilePath = config.getProperty("last.chapter.file", "lastChapter.txt");
        ChapterMonitor monitor = new ChapterMonitor(chapterQueue, baseUrl, lastChapterFilePath, storyName);
        monitorThread = new Thread(monitor);
        monitorThread.start();
        logger.info("ChapterMonitor đã được khởi động.");
    }

    // Luồng xử lý chương mới từ hàng đợi (được đẩy vào bởi ChapterMonitor).
    private void processNewChapters() {
        Set<Integer> processedChapters = new HashSet<>();
        List<Integer> chaptersToProcess = new ArrayList<>();
        long timeoutSeconds = 60; // Thời gian chờ tối đa (giây)

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Thu thập tất cả chương có sẵn
                chapterQueue.drainTo(chaptersToProcess);
                Integer chapter = chapterQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
                if (chapter != null) {
                    chaptersToProcess.add(chapter);
                }

                // Loại bỏ chương trùng lặp
                chaptersToProcess.removeIf(processedChapters::contains);
                processedChapters.addAll(chaptersToProcess);

                // Xử lý nếu có chương
                if (!chaptersToProcess.isEmpty()) {
                    int startChapter = chaptersToProcess.stream().min(Integer::compareTo).orElse(chaptersToProcess.get(0));
                    int endChapter = chaptersToProcess.stream().max(Integer::compareTo).orElse(chaptersToProcess.get(0));
                    logger.info("Xử lý batch chương từ {} đến {} (tổng: {} chương)", startChapter, endChapter, chaptersToProcess.size());
                    processMultipleChapters(startChapter, endChapter);
                    chaptersToProcess.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Lỗi khi xử lý chương mới: {}", e.getMessage(), e);
                notifyFailure("Lỗi xử lý chương mới: " + e.getMessage());
            }
        }
    }
}
