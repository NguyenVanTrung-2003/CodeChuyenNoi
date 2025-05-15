package org.example.codechuyennoi.ProcessVideo;


import org.example.codechuyennoi.ProcessAudio.AudioStory;

public class VideoStory {

    private String videoFilePath; // Example: output/final_video.mp4
    private String title;
    private String description;
    private String youtubeId; // Có thể sẽ được thiết lập sau khi tải lên thành công
    private AudioStory usedAudio;
    private AudioStory audioStory;// Có khả năng trùng lặp hoặc vai trò khác với usedAudio
    private String backgroundAssetPath; // Path to the image/video file used as background
    /**
     * Constructor cho VideoStory.
     * Khởi tạo đối tượng với các chi tiết cốt lõi có sẵn sau khi biên soạn video,
     * không bao gồm youtubeId được đặt sau khi tải lên.
     *
     * @param videoFilePath Đường dẫn đến tệp video đã tạo.
     * @param title Tiêu đề dành cho video.
     * @param description Mô tả dành cho video.
     * @param usedAudio Đối tượng AudioStory được sử dụng để tạo video này.
     * @param audioStory Một tham chiếu khác đến AudioStory (như được thấy trong UML).
     * @param backgroundAssetPath Đường dẫn đến tệp tài sản nền được sử dụng.
     */
    public VideoStory(String videoFilePath, String title, String description, AudioStory usedAudio, AudioStory audioStory, String backgroundAssetPath) {
        this.videoFilePath = videoFilePath;
        this.title = title;
        this.description = description;
        this.usedAudio = usedAudio;
        this.audioStory = audioStory;
        this.backgroundAssetPath = backgroundAssetPath;
        this.youtubeId = null; // Initialize with null
    }
    public VideoStory(String videoFilePath, String title, String description, String youtubeId, AudioStory usedAudio, AudioStory audioStory, String backgroundAssetPath) {
        this.videoFilePath = videoFilePath;
        this.title = title;
        this.description = description;
        this.youtubeId = youtubeId;
        this.usedAudio = usedAudio;
        this.audioStory = audioStory;
        this.backgroundAssetPath = backgroundAssetPath;
    }

    public String getVideoFilePath() {
        return videoFilePath;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getYoutubeId() {
        return youtubeId;
    }

    public AudioStory getUsedAudio() {
        return usedAudio;
    }

    public AudioStory getAudioStory() {
        return audioStory;
    }

    public String getBackgroundAssetPath() {
        return backgroundAssetPath;
    }

    public void setVideoFilePath(String videoFilePath) {
        this.videoFilePath = videoFilePath;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // Bộ thiết lập cho youtubeId rất quan trọng vì nó được thiết lập *sau* khi video được tải lên.
    public void setYoutubeId(String youtubeId) {
        this.youtubeId = youtubeId;
    }

    public void setUsedAudio(AudioStory usedAudio) {
        this.usedAudio = usedAudio;
    }

    public void setAudioStory(AudioStory audioStory) {
        this.audioStory = audioStory;
    }

    public void setBackgroundAssetPath(String backgroundAssetPath) {
        this.backgroundAssetPath = backgroundAssetPath;
    }
    // Ví dụ phương thức toString() để gỡ lỗi/ghi nhật ký dễ dàng
    @Override
    public String toString() {
        return "VideoStory{" +
                "videoFilePath='" + videoFilePath + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", youtubeId='" + youtubeId + '\'' +
                ", usedAudio=" + (usedAudio != null ? usedAudio.getClass().getSimpleName() + "@" + Integer.toHexString(usedAudio.hashCode()) : "null") + // Avoid infinite loop if AudioStory has a toString() that refers back
                ", audioStory=" + (audioStory != null ? audioStory.getClass().getSimpleName() + "@" + Integer.toHexString(audioStory.hashCode()) : "null") +
                ", backgroundAssetPath='" + backgroundAssetPath + '\'' +
                '}';
    }
}
