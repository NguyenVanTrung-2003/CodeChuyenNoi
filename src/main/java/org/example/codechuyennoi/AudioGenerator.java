package org.example.codechuyennoi;


import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;

public class AudioGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerator.class);

    public AudioStory generateAudio(String processedText) {
        if (processedText == null || processedText.isEmpty()) {
            logger.warn("Văn bản đã xử lý rỗng");
            return null;
        }
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            logger.info("Đang tạo âm thanh từ văn bản");
            SynthesisInput input = SynthesisInput.newBuilder().setText(processedText).build();
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("vi-VN")
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build();
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();
            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContent = response.getAudioContent();
            String outputPath = "output/audio_" + System.currentTimeMillis() + ".mp3";
            try (FileOutputStream out = new FileOutputStream(outputPath)) {
                audioContent.writeTo(out);
            }
            logger.info("Đã tạo file âm thanh tại: {}", outputPath);
            return new AudioStory(outputPath);
        } catch (IOException e) {
            logger.error("Lỗi khi tạo âm thanh: {}", e.getMessage());
            return null;
        }
    }
}
