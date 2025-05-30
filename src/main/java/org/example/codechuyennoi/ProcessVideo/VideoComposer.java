package org.example.codechuyennoi.ProcessVideo;

import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private final String ffmpegPath;
    private final String ffprobePath;

    public VideoComposer(String ffmpegPath, String ffprobePath) {
        this.ffmpegPath = validateExecutable(ffmpegPath, "ffmpeg.path");
        this.ffprobePath = validateExecutable(ffprobePath, "ffprobe.path");
    }

    /**
     * Validates that the executable exists and is a file.
     */
    private String validateExecutable(String path, String name) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException(name + " executable not found at: " + path);
        }
        return path;
    }

    public VideoStory composeVideo(Story story, AudioStory audioStory, List<String> imagePaths, String title, String description) {
        if (story == null || audioStory == null || imagePaths == null || imagePaths.isEmpty()) {
            logger.warn("Thiếu đầu vào để tổng hợp video");
            return null;
        }

        // Validate image files
        for (String imagePath : imagePaths) {
            if (!Files.exists(Paths.get(imagePath))) {
                logger.error("Image file does not exist: {}", imagePath);
                return null;
            }
        }

        logger.info("Đang ghép video cho truyện '{}', chương {}", story.getStoryName(), story.getChapterNumber());
        logger.info("File âm thanh: {}", audioStory.getAudioFilePath());

        try {
            // Normalize story name for folder
            String storyFolderName = "video_" + story.getStoryName()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            String outputDir = "output/" + storyFolderName;
            File dir = new File(outputDir);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.error("Không tạo được thư mục đầu ra: {}", outputDir);
                return null;
            }

            String outputPath = outputDir + "/video_chuong_" + story.getChapterNumber() + ".mp4";
            String slideshowPath = outputDir + "/slideshow_" + story.getChapterNumber() + ".mp4";
            File slideshowInput = new File(outputDir + "/slideshow_input.txt");

            // Get audio duration
            double totalDuration = getAudioDuration(audioStory.getAudioFilePath());
            if (totalDuration <= 0) {
                logger.error("Thời lượng audio không hợp lệ: {}", totalDuration);
                return null;
            }
            double imageDuration = totalDuration / imagePaths.size();

            // Create slideshow input file
            try (PrintWriter writer = new PrintWriter(slideshowInput)) {
                for (String path : imagePaths) {
                    writer.println("file '" + path.replace("\\", "/") + "'");
                    writer.println("duration " + imageDuration);
                }
                writer.println("file '" + imagePaths.get(imagePaths.size() - 1).replace("\\", "/") + "'");
            }

            // Create slideshow video
            List<String> cmd1 = List.of(
                    ffmpegPath, "-y",
                    "-f", "concat", "-safe", "0",
                    "-i", slideshowInput.getAbsolutePath(),
                    "-vsync", "vfr", "-pix_fmt", "yuv420p",
                    slideshowPath
            );
            runCommand(cmd1, "Tạo slideshow video");

            // Merge slideshow with audio
            List<String> cmd2 = List.of(
                    ffmpegPath, "-y",
                    "-i", slideshowPath,
                    "-i", audioStory.getAudioFilePath(),
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-shortest",
                    outputPath
            );
            runCommand(cmd2, "Ghép slideshow với audio");

            File outFile = new File(outputPath);
            if (outFile.exists() && outFile.length() > 0) {
                logger.info("Đã tạo video tại: {}", outputPath);
                // Clean up temporary files
                cleanupTempFiles(slideshowInput, new File(slideshowPath));
                return new VideoStory(outputPath, title, description, null, audioStory, audioStory, null);
            } else {
                logger.error("File video đầu ra không tồn tại hoặc rỗng: {}", outputPath);
                cleanupTempFiles(slideshowInput, new File(slideshowPath));
                return null;
            }
        } catch (Exception e) {
            logger.error("Lỗi khi tổng hợp video: {}", e.getMessage(), e);
            return null;
        }
    }

    private double getAudioDuration(String audioPath) throws IOException, InterruptedException {
        if (!Files.exists(Paths.get(audioPath))) {
            throw new IOException("File âm thanh không tồn tại: " + audioPath);
        }
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioPath
        );
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            int exitCode = process.waitFor();
            if (exitCode != 0 || line == null || line.isEmpty()) {
                throw new IOException("Không lấy được thời lượng âm thanh từ ffprobe. Exit code: " + exitCode);
            }
            return Double.parseDouble(line);
        }
    }

    private void runCommand(List<String> command, String operation) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder ffmpegOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ffmpegOutput.append(line).append("\n");
                logger.debug("ffmpeg: {}", line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.error("FFmpeg thất bại khi {}:\n{}", operation, ffmpegOutput);
            throw new RuntimeException("FFmpeg thất bại với mã lỗi: " + exitCode);
        }
    }

    private void cleanupTempFiles(File... files) {
        for (File file : files) {
            if (file.exists() && !file.delete()) {
                logger.warn("Không xóa được file tạm: {}", file.getAbsolutePath());
            }
        }
    }
}