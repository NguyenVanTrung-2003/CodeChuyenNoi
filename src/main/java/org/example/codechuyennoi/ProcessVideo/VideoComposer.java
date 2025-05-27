package org.example.codechuyennoi.ProcessVideo;

import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

/** Lớp VideoComposer chịu trách nhiệm tổng hợp video từ:
 * - Story (thông tin truyện/chương)
 * - AudioStory (file âm thanh)
 * - Danh sách ảnh để tạo slideshow
 * Sử dụng FFmpeg để tạo slideshow từ ảnh, sau đó ghép với audio thành video cuối cùng.
 */
public class VideoComposer {
    private static final Logger logger = LoggerFactory.getLogger(VideoComposer.class);

    private final String ffmpegPath;  // Đường dẫn đến ffmpeg.exe
    private final String ffprobePath; // Đường dẫn đến ffprobe.exe dùng để lấy thông tin media

     // Constructor khởi tạo VideoComposer với đường dẫn ffmpeg và ffprobe.

    public VideoComposer(String ffmpegPath, String ffprobePath) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    /**  Phương thức composeVideo tổng hợp video từ truyện, audio và danh sách ảnh.
     *
     * @param story Thông tin truyện (tên truyện, chương, ...)
     * @param audioStory File âm thanh đã tạo từ text
     * @param imagePaths Danh sách đường dẫn ảnh sẽ dùng làm slideshow
     * @return VideoStory chứa thông tin video đã tạo hoặc null nếu lỗi
     */
    public VideoStory composeVideo(Story story, AudioStory audioStory, List<String> imagePaths, String title, String description) {
        // Kiểm tra đầu vào bắt buộc
        if (story == null || audioStory == null || imagePaths == null || imagePaths.isEmpty()) {
            logger.warn("Thiếu đầu vào để tổng hợp video");
            return null;
        }

        logger.info("Đang ghép video cho truyện '{}', chương {}", story.getStoryName(), story.getChapterNumber());
        logger.info("File âm thanh: {}", audioStory.getAudioFilePath());

        String outputPath = "output/video_" + System.currentTimeMillis() + ".mp4";   // Video đầu ra cuối cùng
        String slideshowPath = "output/slideshow_" + story.getChapterNumber() + ".mp4"; // File slideshow trung gian

        try {
            // Lấy độ dài audio để tính thời gian hiển thị mỗi ảnh (chia đều)
            double totalDuration = getAudioDuration(audioStory.getAudioFilePath());
            double imageDuration = totalDuration / imagePaths.size();

            // Tạo file input danh sách ảnh cho ffmpeg (định dạng concat)
            File slideshowInput = new File("slideshow_input.txt");
            try (PrintWriter writer = new PrintWriter(slideshowInput)) {
                for (String path : imagePaths) {
                    // ffmpeg yêu cầu đường dẫn file dạng Unix-style (dùng /)
                    writer.println("file '" + path.replace("\\", "/") + "'");
                    // Thời gian hiển thị mỗi ảnh
                    writer.println("duration " + imageDuration);
                }
                // Đảm bảo ảnh cuối cùng cũng được hiển thị
                writer.println("file '" + imagePaths.get(imagePaths.size() - 1).replace("\\", "/") + "'");
            }

            // Bước 1: Tạo slideshow video từ ảnh theo file input vừa tạo
            List<String> cmd1 = List.of(
                    ffmpegPath, "-y",
                    "-f", "concat", "-safe", "0",
                    "-i", slideshowInput.getAbsolutePath(),
                    "-vsync", "vfr", "-pix_fmt", "yuv420p",
                    slideshowPath
            );
            runCommand(cmd1); // Thực thi lệnh ffmpeg

            // Bước 2: Ghép slideshow video với file audio
            List<String> cmd2 = List.of(
                    ffmpegPath, "-y",
                    "-i", slideshowPath,
                    "-i", audioStory.getAudioFilePath(),
                    "-c:v", "copy",  // Copy video stream không mã hóa lại
                    "-c:a", "aac",   // Mã hóa audio sang AAC
                    "-shortest",     // Kết thúc khi audio hoặc video kết thúc (dùng để đồng bộ)
                    outputPath
            );
            runCommand(cmd2);

            // Kiểm tra file video đầu ra
            File outFile = new File(outputPath);
            if (outFile.exists()) {
                logger.info("Đã tạo video tại: {}", outputPath);
                // Tạo đối tượng VideoStory trả về (tham số null bạn có thể sửa theo class bạn định nghĩa)
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

    /** Lấy độ dài (thời gian) của file audio bằng ffprobe.
     *
     * @param audioPath đường dẫn file audio
     * @return thời lượng file audio (đơn vị giây)
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

    /** Thực thi lệnh FFmpeg hoặc ffprobe được truyền vào dưới dạng List<String>.
     * Ghi log output của ffmpeg, kiểm tra mã thoát.
     *
     * @param command danh sách chuỗi lệnh để chạy
     * @throws IOException, InterruptedException nếu lệnh không thành công
     */
    private void runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // Gộp cả output lỗi vào output chuẩn
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
