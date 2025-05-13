package org.example.codechuyennoi;


import org.example.codechuyennoi.AudioStory;
import org.example.codechuyennoi.Story;
import org.example.codechuyennoi.VideoStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VideoComposer {
    private static final Logger logger = LoggerFactory.getLogger(VideoComposer.class);
    private final String ffmpegPath;

    public VideoComposer(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public VideoStory composeVideo(Story story, AudioStory audioStory, String backgroundPath) {
        if (story == null || audioStory == null || backgroundPath == null) {
            logger.warn("Thiếu đầu vào để tổng hợp video");
            return null;
        }
        try {
            logger.info("Đang tổng hợp video");
            String outputPath = "output/video_" + System.currentTimeMillis() + ".mp4";
            // Placeholder: Gọi FFmpeg để kết hợp âm thanh, văn bản, và nền
            String command = String.format("%s -i %s -i %s -vf \"drawtext=text='%s':fontcolor=white:fontsize=24:x=10:y=10\" -c:v libx264 -c:a aac %s",
                    ffmpegPath, backgroundPath, audioStory.getAudioFilePath(), story.getProcessedText(), outputPath);
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            if (new File(outputPath).exists()) {
                logger.info("Đã tạo video tại: {}", outputPath);
                return new VideoStory(outputPath);
            } else {
                logger.error("Không thể tạo video");
                return null;
            }
        } catch (Exception e) {
            logger.error("Lỗi khi tổng hợp video: {}", e.getMessage());
            return null;
        }
    }
}