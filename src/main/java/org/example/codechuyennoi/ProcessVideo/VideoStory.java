package org.example.codechuyennoi.ProcessVideo;

import lombok.AllArgsConstructor;
import org.example.codechuyennoi.ProcessAudio.AudioStory;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
public class VideoStory {
    private String videoFilePath;
    private String title;
    private String description;
    private String youtubeId;
    private AudioStory usedAudio;
    private AudioStory audioStory;
    private String backgroundAssetPath;
}
