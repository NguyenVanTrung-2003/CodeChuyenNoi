package org.example.codechuyennoi.ProcessVideo;

import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.example.codechuyennoi.ProcessingSubtitle.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Service
public class VideoComposer {

    private static final Logger logger = LoggerFactory.getLogger(VideoComposer.class);

    private final SubtitleLineProvider subtitleLineProvider;
    private final SubtitleGenerator subtitleGenerator;
    private final String ffmpegPath;
    private final String ffprobePath;

    public VideoComposer(
            SubtitleLineProvider subtitleLineProvider,
            SubtitleGenerator subtitleGenerator,
            @Value("${ffmpeg.path}") String ffmpegPath,
            @Value("${ffprobe.path}") String ffprobePath
    ) {
        this.subtitleLineProvider = subtitleLineProvider;
        this.subtitleGenerator = subtitleGenerator;
        this.ffmpegPath = validateExecutable(ffmpegPath, "ffmpeg.path");
        this.ffprobePath = validateExecutable(ffprobePath, "ffprobe.path");
    }

    private String validateExecutable(String path, String name) {
        File file = new File(path);
        if (!file.exists() || !file.isFile() || !file.canExecute()) {
            throw new IllegalArgumentException(name + " executable not found or not executable at: " + path);
        }
        return path;
    }

    /**
     * Tạo video slideshow ghép âm thanh, sau đó ghép phụ đề karaoke nếu có.

    public VideoStory composeVideoWithSubtitles(Story story, AudioStory audioStory, List<String> imagePaths, String title, String description) throws IOException {
        VideoStory videoStory = composeVideo(story, audioStory, imagePaths, title, description);
        if (videoStory == null) return null;

        String outputPath = videoStory.getVideoFilePath();
        String outputDir = new File(outputPath).getParent();

        // Lấy phụ đề
        List<SubtitleLine> subtitleLines = subtitleLineProvider.getLinesForChapter(story.getStoryName(), story.getChapterNumber());
        if (subtitleLines == null || subtitleLines.isEmpty()) {
            logger.warn("Không có phụ đề cho truyện '{}', chương {}. Trả về video không phụ đề.", story.getStoryName(), story.getChapterNumber());
            return videoStory;
        }

        // Tạo file .ass phụ đề
        String subtitlePath = outputDir + "/subtitles_" + story.getChapterNumber() + ".ass";
        subtitleGenerator.generateAss(subtitleLines, subtitlePath);

        File assFile = new File(subtitlePath);
        if (!assFile.exists()) {
            logger.warn("File phụ đề .ass không được tạo: {}. Trả về video không phụ đề.", subtitlePath);
            return videoStory;
        }

        // Kiểm tra nội dung file .ass
        try {
            List<String> assLines = Files.readAllLines(assFile.toPath());
            if (assLines.isEmpty() || !assLines.get(0).startsWith("[Script Info]")) {
                logger.warn("File .ass không hợp lệ: {}. Trả về video không phụ đề.", subtitlePath);
                return videoStory;
            }
        } catch (IOException e) {
            logger.error("Lỗi khi đọc file .ass {}: {}", subtitlePath, e.getMessage());
            return videoStory;
        }

        logger.info("Ghép phụ đề karaoke vào video...");
        File tempOutput = new File(outputDir + "/temp_video_with_subs.mp4");

        try {
            // Chuẩn hóa đường dẫn file .ass
            String assFilePath = assFile.getAbsolutePath().replace("\\", "/").replace(":", "\\:").replace("'", "\\'");
            String subtitleFilter = "subtitles='" + assFilePath + "'";

            List<String> command = List.of(
                    ffmpegPath, "-y",
                    "-i", outputPath,
                    "-vf", subtitleFilter,
                    "-c:v", "libx264", "-preset", "fast",
                    "-c:a", "copy",
                    tempOutput.getAbsolutePath()
            );
            logger.info("Chạy lệnh FFmpeg: {}", String.join(" ", command));
            runCommand(command, "Ghép phụ đề karaoke vào video");

            Files.deleteIfExists(Paths.get(outputPath));
            Files.move(tempOutput.toPath(), Paths.get(outputPath));

            logger.info("Đã ghép phụ đề và ghi đè video tại: {}", outputPath);
            return new VideoStory(outputPath, title, description, null, audioStory, audioStory, null);

        } catch (IOException | InterruptedException e) {
            logger.error("Lỗi khi ghép phụ đề: {}", e.getMessage(), e);
            Files.deleteIfExists(tempOutput.toPath());
            return videoStory;
        }
    }  */

    public VideoStory composeVideo(Story story, AudioStory audioStory, List<String> imagePaths, String title, String description) {
        if (story == null || audioStory == null || imagePaths == null || imagePaths.isEmpty()) {
            logger.warn("Thiếu đầu vào để tổng hợp video");
            return null;
        }

        for (String imagePath : imagePaths) {
            if (!Files.exists(Paths.get(imagePath))) {
                logger.error("Image file does not exist: {}", imagePath);
                return null;
            }
        }

        logger.info("Đang ghép video cho truyện '{}', chương {}", story.getStoryName(), story.getChapterNumber());
        logger.info("File âm thanh: {}", audioStory.getAudioFilePath());

        try {
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

            double totalDuration = getAudioDuration(audioStory.getAudioFilePath());
            if (totalDuration <= 0) {
                logger.error("Thời lượng audio không hợp lệ: {}", totalDuration);
                return null;
            }
            double imageDuration = totalDuration / imagePaths.size();

            try (PrintWriter writer = new PrintWriter(slideshowInput)) {
                for (String path : imagePaths) {
                    String sanitizedPath = path.replace("'", "'\\''").replace("\\", "/");
                    writer.println("file '" + sanitizedPath + "'");
                    writer.println("duration " + imageDuration);
                }
                String lastImagePath = imagePaths.get(imagePaths.size() - 1).replace("'", "'\\''").replace("\\", "/");
                writer.println("file '" + lastImagePath + "'");
            }

            runCommand(List.of(
                    ffmpegPath, "-y",
                    "-f", "concat", "-safe", "0",
                    "-i", slideshowInput.getAbsolutePath(),
                    "-vsync", "vfr", "-pix_fmt", "yuv420p",
                    slideshowPath
            ), "Tạo slideshow video");

            runCommand(List.of(
                    ffmpegPath, "-y",
                    "-i", slideshowPath,
                    "-i", audioStory.getAudioFilePath(),
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-shortest",
                    outputPath
            ), "Ghép slideshow với audio");

            File outFile = new File(outputPath);
            if (outFile.exists() && outFile.length() > 0) {
                logger.info("Đã tạo video tại: {}", outputPath);
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
            if (exitCode != 0 || line == null || line.trim().isEmpty()) {
                throw new IOException("Không lấy được thời lượng âm thanh từ ffprobe. Exit code: " + exitCode);
            }
            return Double.parseDouble(line.trim());
        }
    }

    private void runCommand(List<String> command, String operation) throws IOException, InterruptedException {
        logger.info("Chạy lệnh FFmpeg: {}", String.join(" ", command));
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