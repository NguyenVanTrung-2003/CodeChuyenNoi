package org.example.codechuyennoi.ProcessAudio;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TextToSpeech {
    private static final Logger logger = LoggerFactory.getLogger(TextToSpeech.class);
    private static final String API_URL = "https://speech.aiservice.vn/tts/tools/demo";

    public ByteString synthesize(String text) throws IOException, InterruptedException {

        // Tạo JSON body đúng định dạng
        String json = String.format("""
                {
                  "text":"%s",
                  "voice": "hcm_thanhthao",
                  "speed": "1.0"
                }
                """, text.replace("\"", "\\\""));

        // Tạo POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gọi API thất bại: " + response.body());
        } else {
            System.out.println("✅ Gọi API thành công: " + response.body());
        }

        // Parse JSON để lấy link audio
        String audioUrl = response.body().split("\"")[3];  // đơn giản, không dùng JSON parser
        System.out.println("🎧 Link audio: " + audioUrl);

        // Tải dữ liệu âm thanh từ link
        URL url = new URL(audioUrl);
        try (InputStream in = url.openStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
            return ByteString.copyFrom(baos.toByteArray());
        }
    }
}