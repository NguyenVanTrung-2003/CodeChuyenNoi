package org.example.codechuyennoi.Workflow;

import org.example.codechuyennoi.Integation.VideoMetadata;
import org.example.codechuyennoi.Integation.YouTubeUploader;
import org.example.codechuyennoi.Notification.ChapterMonitor;
import org.example.codechuyennoi.Notification.NotificationService;
import org.example.codechuyennoi.ProcessAudio.AudioGenerator;
import org.example.codechuyennoi.ProcessAudio.AudioProcessor;
import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.example.codechuyennoi.ProcessStory.StoryProcessor;
import org.example.codechuyennoi.ProcessVideo.VideoComposer;
import org.example.codechuyennoi.ProcessVideo.VideoStory;
import org.example.codechuyennoi.Image.AiImageGenerator;
import org.example.codechuyennoi.ProcessVideo.VideoMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class WorkflowCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowCoordinator.class);
    private static final int MAX_CHAPTERS_PER_BATCH = 100;

    private final StoryProcessor storyProcessor;
    private final AudioGenerator audioGenerator;
    private final AudioProcessor audioProcessor;
    private final AiImageGenerator aiImageGenerator;
    private final VideoComposer videoComposer;
    private final YouTubeUploader youTubeUploader;
    private final NotificationService notificationService;
    private final VideoMerger videoMerger;
    private String videoTitle;
    private String videoDescription;

    private final BlockingQueue<Integer> chapterQueue = new LinkedBlockingQueue<>();
    private Thread monitorThread;

    private String storyName;
    private String baseUrl;

    @Value("${last.chapter.file:lastChapter.txt}")
    private String lastChapterFilePath;

    public WorkflowCoordinator(
            StoryProcessor storyProcessor,
            AudioGenerator audioGenerator,
            AudioProcessor audioProcessor,
            AiImageGenerator aiImageGenerator,
            VideoComposer videoComposer,
            YouTubeUploader youTubeUploader,
            NotificationService notificationService,
            VideoMerger videoMerger
    ) {
        this.storyProcessor = storyProcessor;
        this.audioGenerator = audioGenerator;
        this.audioProcessor = audioProcessor;
        this.aiImageGenerator = aiImageGenerator;
        this.videoComposer = videoComposer;
        this.youTubeUploader = youTubeUploader;
        this.notificationService = notificationService;
        this.videoMerger = videoMerger;
    }
    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public void setVideoDescription(String videoDescription) {
        this.videoDescription = videoDescription;
    }
    public void setStoryName(String storyName) {
        this.storyName = storyName;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void start(String storyName, String baseUrl) {
        if (storyName == null || storyName.isEmpty()) {
            throw new IllegalArgumentException("storyName không được để trống");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("baseUrl không được để trống");
        }
        this.storyName = storyName;
        this.baseUrl = baseUrl;

        startChapterMonitoring();
        startProcessingThread();
        logger.info("WorkflowCoordinator đã khởi động với truyện '{}' và baseUrl '{}'", storyName, baseUrl);
    }

    public void stop() {
        if (monitorThread != null && monitorThread.isAlive()) {
            monitorThread.interrupt();
            try {
                monitorThread.join(5000);
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for monitor thread to stop.");
                Thread.currentThread().interrupt();
            }
        }
    }

    public void processMultipleChapters(int startChapter, int endChapter) throws IOException {
        // Kiểm tra tính hợp lệ của khoảng chương, nếu không hợp lệ thì dừng xử lý
        if (!isValidChapterRange(startChapter, endChapter, baseUrl)) {
            return;
        }
        List<VideoStory> chapterVideos = new ArrayList<>();
        try {
              // Xử lý nhiều chương: crawl và làm sạch văn bản
            List<Story> processedStories = storyProcessor.processChaptersInBatch(
                    storyName, baseUrl, startChapter, endChapter
            );
              // Với mỗi chương đã xử lý, tạo audio và video tương ứng
            for (Story story : processedStories) {
                AudioStory audioStory = generateAudio(story);              // Sinh file âm thanh
                VideoStory chapterVideo = composeVideo(story, audioStory); // Tạo video chương
                // Chỉ thêm video chương nếu tạo thành công
              if (chapterVideo != null) {
                  chapterVideos.add(chapterVideo);
               }
            }
            // Nếu chỉ có 1 chương thì không cần gộp video và upload
            if (chapterVideos.size() <= 1) {
                logger.warn("⚠️ Chỉ có {} chương được xử lý, bỏ qua bước gộp và upload YouTube.", chapterVideos.size());
                notifyFailure("Chỉ có 1 chương được xử lý, không gộp video");
                return;
            }
              // Gộp toàn bộ video các chương thành 1 video hoàn chỉnh
            VideoStory fullVideo = mergeAllChapterVideos(storyName, startChapter, endChapter);
              // Gắn tiêu đề và mô tả nếu được cung cấp
            applyMetadataIfPresent(fullVideo, videoTitle, videoDescription);
              // Tải video lên YouTube, nhận về ID
            String youtubeId = uploadToYouTube(fullVideo, fullVideo.getTitle(), fullVideo.getDescription());
              // Gửi thông báo khi thành công
            sendSuccessNotification(youtubeId);
        } catch (Exception e) {
            // Bắt lỗi và thông báo khi có lỗi bất kỳ trong quá trình xử lý
            logger.error("Lỗi nghiêm trọng trong quy trình batch: {}", e.getMessage(), e);
            notifyFailure("Lỗi hệ thống trong quy trình batch: " + e.getMessage());
        }
        // Log kết thúc quá trình xử lý
        logger.info("Hoàn thành xử lý batch chương.");
    }

    private AudioStory generateAudio(Story story) {
        AudioStory audioStory = audioGenerator.generateAudio(storyName, story.getChapterNumber(), story.getProcessedText());
        return audioProcessor.processAudio(audioStory);
    }

    private VideoStory composeVideo(Story story, AudioStory audioStory) {
        try {
            List<String> prompts = story.getSentences();
            List<String> imagePaths = aiImageGenerator.generateImages(prompts);

            VideoStory chapterVideo = videoComposer.composeVideo(
                    story, audioStory, imagePaths,
                    "Chương " + story.getChapterNumber(),
                    ""
            );
            if (chapterVideo == null) {
                logger.error("❌ Tạo video chương thất bại cho chương số: {}", story.getChapterNumber());
                return null;
            }
            logger.info("✅ Đã tạo video chương {}: {}", story.getChapterNumber(), chapterVideo.getDescription());
            return chapterVideo;
        } catch (Exception e) {
            logger.error("Lỗi khi tạo video chương số {}: {}", story.getChapterNumber(), e.getMessage(), e);
            return null;
        }
    }

    private VideoStory mergeAllChapterVideos(String storyName, int startChapter, int endChapter) {
        String storyFolderName = "video_" + storyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
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

        int expectedChapterCount = Math.min(endChapter - startChapter + 1, MAX_CHAPTERS_PER_BATCH);
        if (videoFiles.length < expectedChapterCount) {
            logger.error("Số file video chương ({}) không khớp với số chương mong đợi ({})",
                    videoFiles.length, expectedChapterCount);
            return null;
        }

        logger.info("🔄 Gộp {} video chương trong thư mục: {}", videoFiles.length, storyFolder);
        String fullVideoPath = videoMerger.mergeVideos(storyFolder);
        if (fullVideoPath == null) {
            logger.error("❌ Gộp video chương thất bại.");
            return null;
        }

        logger.info("✅ Đã gộp video tổng hợp: {}", fullVideoPath);

        return new VideoStory(fullVideoPath,
                "Tổng hợp chương " + startChapter + " đến " + endChapter,
                "Tổng hợp video truyện " + storyName,
                null, null, null, null);
    }

    private String uploadToYouTube(VideoStory videoStory, String videoTitle, String videoDescription) throws IOException {
        if (videoStory == null) {
            logger.error("Không thể tải video lên YouTube vì videoStory là null");
            return null;
        }
        return youTubeUploader.uploadVideo(videoStory, videoTitle, videoDescription);
    }

    private void sendSuccessNotification(String youtubeId) {
        if (youtubeId != null) {
            notificationService.sendCompletionNotification(true, "Quy trình hoàn thành, YouTube ID: " + youtubeId);
        } else {
            logger.warn("Không gửi thông báo thành công vì YouTube ID là null");
        }
    }

    private void notifyFailure(String message) {
        logger.error("Thông báo thất bại: {}", message);
        notificationService.sendCompletionNotification(false, message);
    }
    private boolean isValidChapterRange(int startChapter, int endChapter, String baseUrl) {
        logger.info("Bắt đầu xử lý batch chương từ {} đến {} với base URL: {}", startChapter, endChapter, baseUrl);
        int expectedChapterCount = Math.min(endChapter - startChapter + 1, MAX_CHAPTERS_PER_BATCH);

        if (expectedChapterCount <= 0 || expectedChapterCount > MAX_CHAPTERS_PER_BATCH) {
            logger.error("Số chương không hợp lệ: startChapter={}, endChapter={}, expectedChapterCount={}",
                    startChapter, endChapter, expectedChapterCount);
            notifyFailure("Số chương không hợp lệ: " + expectedChapterCount);
            return false;
        }

        return true;
    }
    private void applyMetadataIfPresent(VideoStory video, String title, String description) {
        if (title != null && !title.isEmpty()) {
            video.setTitle(title);
        }
        if (description != null && !description.isEmpty()) {
            video.setDescription(description);
        }
    }
    private void startChapterMonitoring() {
        ChapterMonitor monitor = new ChapterMonitor(chapterQueue, baseUrl);
        monitor.initWithStoryName(storyName);
        monitorThread = new Thread(monitor, "ChapterMonitorThread");
        monitorThread.setDaemon(true);
        monitorThread.start();
        logger.info("ChapterMonitor đã được khởi động cho truyện: {}", storyName);
    }

    private void startProcessingThread() {
        Thread processingThread = new Thread(this::processNewChapters, "ChapterProcessingThread");
        processingThread.setDaemon(true);
        processingThread.start();
    }

    private void processNewChapters() {
        Set<Integer> processedChapters = new HashSet<>();
        List<Integer> chaptersToProcess = new ArrayList<>();
        long timeoutSeconds = 60;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                chapterQueue.drainTo(chaptersToProcess);
                Integer chapter = chapterQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
                if (chapter != null) {
                    chaptersToProcess.add(chapter);
                }

                chaptersToProcess.removeIf(processedChapters::contains);
                processedChapters.addAll(chaptersToProcess);

                if (!chaptersToProcess.isEmpty()) {
                    int startChapter = Collections.min(chaptersToProcess);
                    int endChapter = Collections.max(chaptersToProcess);

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
