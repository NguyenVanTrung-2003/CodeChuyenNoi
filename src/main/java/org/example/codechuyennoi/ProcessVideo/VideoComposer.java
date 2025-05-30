package org.example.codechuyennoi.ProcessVideo;

import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

/**
 * Lớp VideoComposer chịu trách nhiệm tổng hợp video từ:
 * - Story (thông tin truyện/chương)
 * - AudioStory (file âm thanh)
 * - Danh sách ảnh để tạo slideshow
 * Sử dụng FFmpeg để tạo slideshow từ ảnh, sau đó ghép với audio thành video cuối cùng.
 */
public class VideoComposer {
    private static final Logger logger = LoggerFactory.getLogger(VideoComposer.class);

    private final String ffmpegPath;  // Đường dẫn đến ffmpeg.exe
    private final String ffprobePath; // Đường dẫn đến ffprobe.exe dùng để lấy thông tin media
    private  final VideoMerger videoMerger;
    // Constructor khởi tạo VideoComposer với đường dẫn ffmpeg và ffprobe.
    public VideoComposer(String ffmpegPath, String ffprobePath, VideoMerger videoMerger) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.videoMerger = new VideoMerger(ffmpegPath);
    }

    /**
     * Phương thức composeVideo tổng hợp video từ truyện, audio và danh sách ảnh.
     *
     * @param story       Thông tin truyện (tên truyện, chương, ...)
     * @param audioStory  File âm thanh đã tạo từ text
     * @param imagePaths  Danh sách đường dẫn ảnh sẽ dùng làm slideshow
     * @param title       Tiêu đề video
     * @param description Mô tả video
     * @return VideoStory chứa thông tin video đã tạo hoặc null nếu lỗi
     */
    public VideoStory composeVideo(Story story, AudioStory audioStory, List<String> imagePaths, String title, String description) {
        if (story == null || audioStory == null || imagePaths == null || imagePaths.isEmpty()) {
            logger.warn("Thiếu đầu vào để tổng hợp video");
            return null;
        }

        logger.info("Đang ghép video cho truyện '{}', chương {}", story.getStoryName(), story.getChapterNumber());
        logger.info("File âm thanh: {}", audioStory.getAudioFilePath());

        try {
            // Chuẩn hóa tên thư mục theo storyName: chữ thường, thay khoảng trắng bằng gạch dưới, bỏ dấu
            String storyFolderName = "video_" + story.getStoryName();
            String outputDir = "output/" + storyFolderName;
            File dir = new File(outputDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    logger.error("Không tạo được thư mục đầu ra: {}", outputDir);
                    return null;
                }
            }

            // Đường dẫn file video đầu ra nằm trong thư mục của truyện
            String outputPath = outputDir + "/video_chuong_" + story.getChapterNumber() + ".mp4";
            String slideshowPath = outputDir + "/slideshow_" + story.getChapterNumber() + ".mp4";

            // Lấy thời lượng audio
            double totalDuration = getAudioDuration(audioStory.getAudioFilePath());
            double imageDuration = totalDuration / imagePaths.size();

            // Tạo file input cho ffmpeg dạng concat
            File slideshowInput = new File(outputDir + "/slideshow_input.txt");
            try (PrintWriter writer = new PrintWriter(slideshowInput)) {
                for (String path : imagePaths) {
                    writer.println("file '" + path.replace("\\", "/") + "'");
                    writer.println("duration " + imageDuration);
                }
                // Ảnh cuối cùng được lặp lại để ffmpeg xử lý đúng
                writer.println("file '" + imagePaths.get(imagePaths.size() - 1).replace("\\", "/") + "'");
            }

            // Tạo slideshow video từ ảnh
            List<String> cmd1 = List.of(
                    ffmpegPath, "-y",
                    "-f", "concat", "-safe", "0",
                    "-i", slideshowInput.getAbsolutePath(),
                    "-vsync", "vfr", "-pix_fmt", "yuv420p",
                    slideshowPath
            );
            runCommand(cmd1);

            // Ghép slideshow với audio
            List<String> cmd2 = List.of(
                    ffmpegPath, "-y",
                    "-i", slideshowPath,
                    "-i", audioStory.getAudioFilePath(),
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-shortest",
                    outputPath
            );
            runCommand(cmd2);

            File outFile = new File(outputPath);
            if (outFile.exists()) {
                logger.info("Đã tạo video tại: {}", outputPath);
                return new VideoStory(outputPath, title, description, null, audioStory, audioStory, null);
            } else {
                logger.error("Không thể tạo video");
                return null;
            }
        } catch (Exception e) {
            logger.error("Lỗi khi tổng hợp video: {}", e.getMessage(), e);
            return null;
        }
    }
    public String mergeChapterVideos(String storyFolderPath) {
        logger.info("Bắt đầu nối video chương trong thư mục: {}", storyFolderPath);
        String mergedVideoPath = videoMerger.mergeVideos(storyFolderPath);
        if (mergedVideoPath != null) {
            logger.info("Đã tạo video tổng tại: {}", mergedVideoPath);
        } else {
            logger.error("Nối video chương thất bại.");
        }
        return mergedVideoPath;
    }

    /**
     * Lấy thời lượng file audio sử dụng ffprobe.
     */
    private double getAudioDuration(String audioPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioPath
        );
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        process.waitFor();
        if (line == null || line.isEmpty()) {
            throw new IOException("Không lấy được độ dài âm thanh từ ffprobe.");
        }
        return Double.parseDouble(line);
    }

    /**
     * Chạy lệnh ffmpeg hoặc ffprobe.
     */
    private void runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("ffmpeg: " + line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg thất bại với mã lỗi: " + exitCode);
        }
    }

}
