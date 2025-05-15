
package org.example.codechuyennoi.ProcessVideo;

import org.example.codechuyennoi.ProcessAudio.AudioStory;
import org.example.codechuyennoi.ProcessStory.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VideoComposer {
    private static final Logger logger = LoggerFactory.getLogger(VideoComposer.class);
    private final String ffmpegPath;

    public VideoComposer(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public VideoStory composeVideo(Story story, AudioStory audioStory, String backgroundPath, String title, String description) {
        if (story == null || audioStory == null || backgroundPath == null) {
            logger.warn("Thiếu đầu vào để tổng hợp video");
            return null;
        }

        try {
            logger.info("Đang tổng hợp video...");
            String outputPath = "output/video_" + System.currentTimeMillis() + ".mp4";

            // Chuẩn bị lệnh ffmpeg dạng List để tránh lỗi parsing
            // Giả sử bạn muốn ghép background video/hình + audio rồi thêm text overlay
            // Đây là câu lệnh mẫu cơ bản, bạn có thể chỉnh lại theo nhu cầu:
            // ffmpeg -i backgroundPath -i audioPath -filter_complex "drawtext=..." -c:v libx264 -c:a aac -shortest outputPath

            String text = story.getProcessedText().replace(":", "\\:").replace("'", "\\'");

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", backgroundPath,
                    "-i", audioStory.getAudioFilePath(),
                    "-filter_complex", "drawtext=text='" + text + "':fontcolor=white:fontsize=24:x=10:y=10",
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-shortest",
                    outputPath
            );

            pb.redirectErrorStream(true);  // Gộp stderr vào stdout để đọc dễ hơn
            Process process = pb.start();

            // Đọc output từ ffmpeg để debug
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("ffmpeg: " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("ffmpeg process kết thúc với mã lỗi: " + exitCode);
                return null;
            }

            File outFile = new File(outputPath);
            if (outFile.exists()) {
                logger.info("Đã tạo video tại: {}", outputPath);
                return new VideoStory(outputPath, title, description, null, audioStory, audioStory, backgroundPath);
            } else {
                logger.error("Không thể tạo video");
                return null;
            }

        } catch (Exception e) {
            logger.error("Lỗi khi tổng hợp video: {}", e.getMessage(), e);
            return null;
        }
    }
}
