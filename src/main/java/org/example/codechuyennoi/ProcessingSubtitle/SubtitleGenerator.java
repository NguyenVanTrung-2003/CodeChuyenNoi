package org.example.codechuyennoi.ProcessingSubtitle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Service
public class SubtitleGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SubtitleGenerator.class);

    public File generateAss(List<SubtitleLine> lines, String outputPath) throws IOException {
        logger.info("Tạo file .ass tại: {}", outputPath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Header ASS
            writer.write("[Script Info]\n");
            writer.write("Title: Generated Subtitles\n");
            writer.write("ScriptType: v4.00+\n");
            writer.write("WrapStyle: 0\n");
            writer.write("ScaledBorderAndShadow: yes\n");
            writer.write("PlayResX: 1920\n");
            writer.write("PlayResY: 1080\n\n");

            // Style
            writer.write("[V4+ Styles]\n");
            writer.write("Format: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ");
            writer.write("ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
            writer.write("Style: Default,Arial,48,&H00FFFFFF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,3,0,2,10,10,20,1\n\n");

            // Events
            writer.write("[Events]\n");
            writer.write("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

            for (SubtitleLine line : lines) {
                String start = formatAssTime(line.getStart());
                String end = formatAssTime(line.getEnd());
                String text = line.getText()
                        .replace("\n", " ")        // tránh xuống dòng đột ngột
                        .replace(",", "，");       // dấu phẩy full-width tránh lỗi
                writer.write(String.format("Dialogue: 0,%s,%s,Default,,0,0,0,,%s\n", start, end, text));
            }
        }

        File assFile = new File(outputPath);
        if (assFile.exists()) {
            logger.info("Đã tạo file .ass thành công: {}", outputPath);
        } else {
            logger.error("Không thể tạo file .ass: {}", outputPath);
        }
        return assFile;
    }

    /**
     * Chuyển đổi thời gian giây (double) sang định dạng ASS: H:MM:SS.cs
     */
    private String formatAssTime(double timeSeconds) {
        int hours = (int) (timeSeconds / 3600);
        int minutes = (int) ((timeSeconds % 3600) / 60);
        int seconds = (int) (timeSeconds % 60);
        int centiseconds = (int) ((timeSeconds - (int) timeSeconds) * 100); // 2 chữ số sau dấu chấm
        return String.format("%01d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds);
    }
}
