package org.example.codechuyennoi.ProcessVideo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoMerger {
    private static final Logger logger = LoggerFactory.getLogger(VideoMerger.class);

    private final String ffmpegPath;

    // Inject giá trị ffmpegPath từ application.properties hoặc application.yml
    public VideoMerger(@Value("${ffmpeg.path}") String ffmpegPath) {
        this.ffmpegPath = validateExecutable(ffmpegPath, "FFmpeg");
    }

    private String validateExecutable(String path, String name) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException(name + " executable not found at: " + path);
        }
        return path;
    }

    public String mergeVideos(String storyFolderPath) {
        if (storyFolderPath == null || storyFolderPath.isBlank()) {
            logger.error("Đường dẫn thư mục trống hoặc null");
            return null;
        }

        Path folderPath = Paths.get(storyFolderPath).toAbsolutePath().normalize();
        File folder = folderPath.toFile();
        if (!folder.isDirectory() || !Files.isReadable(folderPath) || !Files.isWritable(folderPath)) {
            logger.error("Thư mục không tồn tại, không đọc được hoặc không ghi được: {}", folderPath);
            return null;
        }

        File[] videoFilesArray = folder.listFiles((dir, name) -> name.matches("video_chuong_\\d+\\.mp4"));
        if (videoFilesArray == null || videoFilesArray.length == 0) {
            logger.warn("Không tìm thấy file video chương trong thư mục: {}", folderPath);
            return null;
        }

        List<File> videoFiles = Arrays.stream(videoFilesArray)
                .filter(file -> file.length() > 0)
                .sorted(Comparator.comparingInt(this::extractChapterNumber))
                .collect(Collectors.toList());

        if (videoFiles.isEmpty()) {
            logger.error("Không có file video chương hợp lệ để nối trong thư mục: {}", folderPath);
            return null;
        }

        logger.info("Tìm thấy {} file video chương:", videoFiles.size());
        videoFiles.forEach(f -> logger.info(" - {} ({} bytes)", f.getName(), f.length()));

        File concatFile = new File(folder, "concat_list.txt");
        try (PrintWriter writer = new PrintWriter(concatFile)) {
            for (File file : videoFiles) {
                String filePath = file.getCanonicalPath().replace("\\", "/");
                writer.println("file '" + filePath + "'");
            }
            logger.info("Đã tạo file concat_list.txt tại: {}", concatFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Lỗi ghi file concat_list.txt: {}", e.getMessage(), e);
            return null;
        }

        Path mergedVideoPath = folderPath.resolve("video_full.mp4").toAbsolutePath().normalize();
        File outputFile = mergedVideoPath.toFile();

        if (outputFile.exists()) {
            try {
                Files.delete(outputFile.toPath());
                logger.info("Đã xóa file output cũ: {}", mergedVideoPath);
            } catch (IOException e) {
                logger.error("Không thể xóa file output cũ: {}. Vui lòng kiểm tra quyền truy cập.", mergedVideoPath, e);
                return null;
            }
        }

        Path outputDir = mergedVideoPath.getParent();
        if (!Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
                logger.info("Đã tạo thư mục đầu ra: {}", outputDir);
            } catch (IOException e) {
                logger.error("Không thể tạo thư mục đầu ra: {}", outputDir, e);
                return null;
            }
        }
        if (!Files.isWritable(outputDir)) {
            logger.error("Thư mục đầu ra không có quyền ghi: {}", outputDir);
            return null;
        }

        List<String> command = List.of(
                ffmpegPath,
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile.getAbsolutePath(),
                "-c", "copy",
                mergedVideoPath.toString().replace("\\", "/")
        );

        logger.info("Đang chạy lệnh FFmpeg: {}", String.join(" ", command));
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(folder);
            pb.redirectErrorStream(true);
            process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("ffmpeg: {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                if (outputFile.exists() && outputFile.length() > 0) {
                    logger.info("✅ Đã tạo video dài thành công: {}", mergedVideoPath);
                    return mergedVideoPath.toString();
                } else {
                    logger.error("❌ FFmpeg kết thúc nhưng file output không được tạo hoặc rỗng: {}", mergedVideoPath);
                    logConcatFileContent(concatFile);
                    return null;
                }
            } else {
                logger.error("❌ FFmpeg thất bại với mã lỗi: {}", exitCode);
                logConcatFileContent(concatFile);
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("❌ Lỗi khi chạy FFmpeg: {}", e.getMessage(), e);
            return null;
        } finally {
            if (process != null) process.destroy();
            cleanupTempFiles(concatFile);
        }
    }

    private int extractChapterNumber(File file) {
        try {
            String name = file.getName();
            int start = name.indexOf("_chuong_") + 8;
            int end = name.lastIndexOf('.');
            return Integer.parseInt(name.substring(start, end));
        } catch (Exception e) {
            logger.warn("Không thể lấy số chương từ file: {}", file.getName());
            return Integer.MAX_VALUE;
        }
    }

    private void logConcatFileContent(File concatFile) {
        try {
            String content = Files.readString(concatFile.toPath());
            logger.error("Nội dung file concat_list.txt:\n{}", content);
        } catch (IOException e) {
            logger.error("Không đọc được file concat_list.txt", e);
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
