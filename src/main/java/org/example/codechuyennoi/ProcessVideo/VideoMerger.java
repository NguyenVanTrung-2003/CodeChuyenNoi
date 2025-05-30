package org.example.codechuyennoi.ProcessVideo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class VideoMerger {

    /**
     * Lớp VideoMerger chịu trách nhiệm nối nhiều video chương
     * thành một video dài duy nhất sử dụng FFmpeg.
     */

    private static final Logger logger = LoggerFactory.getLogger(VideoMerger.class);
    private final String ffmpegPath;  // Đường dẫn đến ffmpeg.exe

    public VideoMerger(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /**
     * Nối tất cả video chương trong thư mục storyFolderPath thành 1 video dài.
     * Các file video chương có định dạng: video_chuong_<số>.mp4
     *
     * @param storyFolderPath Đường dẫn thư mục chứa video chương
     * @return Đường dẫn file video dài đã tạo, hoặc null nếu lỗi
     */
    public String mergeVideos(String storyFolderPath) {
        File folder = new File(storyFolderPath);

        // Đảm bảo thư mục tồn tại
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                logger.error("Không thể tạo thư mục đầu ra: {}", storyFolderPath);
                return null;
            }
        }

        if (!folder.isDirectory()) {
            logger.error("Không phải thư mục hợp lệ: {}", storyFolderPath);
            return null;
        }

        File[] videoFiles = folder.listFiles((dir, name) -> name.matches("video_chuong_\\d+\\.mp4"));
        if (videoFiles == null || videoFiles.length == 0) {
            logger.warn("Không tìm thấy file video chương trong thư mục: {}", storyFolderPath);
            return null;
        }

        // Sắp xếp file theo số chương tăng dần
        Arrays.sort(videoFiles, Comparator.comparingInt(f -> {
            String numStr = f.getName().replaceAll("\\D+", "");
            return Integer.parseInt(numStr);
        }));

        // Tạo file concat_list.txt chứa danh sách video cho FFmpeg
        File concatFile = new File(folder, "concat_list.txt");
        try (PrintWriter writer = new PrintWriter(concatFile)) {
            for (File file : videoFiles) {
                String filePath = file.getAbsolutePath().replace("\\", "/");
                writer.println("file '" + filePath + "'");
            }
        } catch (IOException e) {
            logger.error("Lỗi ghi file concat_list.txt: {}", e.getMessage(), e);
            return null;
        }

        // Đường dẫn file video đầu ra
        String mergedVideoPath = storyFolderPath + "/video_full.mp4";

        // Lệnh gọi ffmpeg
        List<String> command = List.of(
                ffmpegPath, "-f", "concat", "-safe", "0",
                "-i", concatFile.getAbsolutePath(),
                "-c", "copy",
                mergedVideoPath
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(folder);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("ffmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("✅ Đã tạo video dài: {}", mergedVideoPath);
                return mergedVideoPath;
            } else {
                logger.error("❌ FFmpeg nối video thất bại với mã lỗi: {}", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("❌ Lỗi khi chạy FFmpeg nối video: {}", e.getMessage(), e);
        }

        return null;
    }
}
