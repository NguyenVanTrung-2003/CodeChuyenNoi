package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TextToSpeech implements AutoCloseable {
    private final String voice;
    private final int speed;

    public TextToSpeech(String voice, int speed) {
        this.voice = voice;
        this.speed = speed;
    }

    public ByteString synthesize(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Văn bản đầu vào trống.");
        }

        // URL encode văn bản
        String data = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&voice=" + URLEncoder.encode(voice, StandardCharsets.UTF_8)
                + "&speed=" + speed;

        URL url = new URL("https://speech.aiservice.vn/tts/api/demo");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        // Gửi request
        conn.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));

        // Đọc response là file audio
        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return ByteString.copyFrom(out.toByteArray());
        }
    }

    @Override
    public void close() {
        // Không cần đóng gì thêm
    }
}
