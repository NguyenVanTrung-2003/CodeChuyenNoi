package org.example.codechuyennoi.ProcessingSubtitle;

import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
@Component
public class AudioDurationExtractor {
    private static final Logger logger = LoggerFactory.getLogger(AudioDurationExtractor.class);

    public double getAudioDuration(String audioPath) {
        try {
            // Sử dụng đường dẫn tuyệt đối và kiểm tra file
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                logger.error("File âm thanh không tồn tại: {}", audioPath);
                return -1.0;
            }

            // Lệnh ffprobe với đường dẫn ffprobe.exe
            ProcessBuilder pb = new ProcessBuilder(
                    "D:/ffmpeg-7.1.1-essentials_build/ffmpeg-7.1.1-essentials_build/bin/ffprobe.exe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    audioFile.getAbsolutePath()
            );
            Process process = pb.start();

            // Đọc output và error
            String output = new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.joining());
            String errorOutput = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.joining());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                logger.error("ffprobe thất bại với mã lỗi: {}. Đường dẫn: {}. Lỗi: {}",
                        exitCode, audioPath, errorOutput);
                return -1.0;
            }

            if (output.isEmpty()) {
                logger.error("ffprobe không trả về thời lượng cho file: {}", audioPath);
                return -1.0;
            }

            return Double.parseDouble(output.trim());
        } catch (Exception e) {
            logger.error("Lỗi khi lấy thời lượng audio: {}", audioPath, e);
            return -1.0;
        }
    }
}