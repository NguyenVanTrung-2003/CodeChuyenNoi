package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class TextToSpeech implements AutoCloseable {
    private final String espeakExecutable;
    private final String voice;
    private final int speed;      // tốc độ nói (words per minute)
    private final int amplitude;  // âm lượng (0–200)

    // ✅ Hàm khởi tạo có đường dẫn tuyệt đối
    public TextToSpeech(String voice, int speed, int amplitude) {
        // Dùng đường dẫn tuyệt đối đến espeak.exe
        this("D:\\eSpeak\\command_line\\espeak.exe", voice, speed, amplitude);
    }

    public TextToSpeech(String espeakExecutable, String voice, int speed, int amplitude) {
        this.espeakExecutable = espeakExecutable;
        this.voice = voice;
        this.speed = speed;
        this.amplitude = amplitude;
    }

    /**
     * Gọi eSpeak để synthesize văn bản thành WAV bytes.
     *
     * @param text văn bản cần đọc
     * @return ByteString chứa dữ liệu WAV
     * @throws Exception nếu quá trình bị lỗi
     */
    public ByteString synthesize(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Văn bản đầu vào trống.");
        }

        // Loại bỏ dấu "
        text = text.replace("\"", "");

        List<String> cmd = new ArrayList<>();
        cmd.add(espeakExecutable);
        cmd.add("--stdin");   // ✅ Đọc văn bản từ stdin
        cmd.add("--stdout");
        cmd.add("-v");
        cmd.add(voice);
        cmd.add("-s");
        cmd.add(String.valueOf(speed));
        cmd.add("-a");
        cmd.add(String.valueOf(amplitude));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = pb.start();

        // ✅ Ghi văn bản vào stdin của eSpeak
        try (var stdin = process.getOutputStream()) {
            stdin.write(text.getBytes());
            stdin.flush();
        }

        // ✅ Đọc WAV đầu ra từ stdout
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = process.getInputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("eSpeak lỗi, mã thoát: " + exitCode);
        }

        return ByteString.copyFrom(baos.toByteArray());
    }



    @Override
    public void close() {
        // Không cần close gì thêm
    }
}
