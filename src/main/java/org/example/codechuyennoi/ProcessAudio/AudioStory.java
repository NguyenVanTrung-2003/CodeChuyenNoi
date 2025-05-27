package org.example.codechuyennoi.ProcessAudio;

/**
 * Lớp AudioStory đại diện cho một file âm thanh đã được tạo từ văn bản của một chương truyện.
 * Được sử dụng để lưu trữ và truyền thông tin về đường dẫn tới file âm thanh.
 */
public class AudioStory {
    // Biến lưu đường dẫn tới file âm thanh (.wav, .mp3, v.v.)
    private String audioFilePath;
    /**
     * Constructor khởi tạo đối tượng AudioStory với đường dẫn file âm thanh.
     *
     * @param audioFilePath đường dẫn tới file âm thanh đã tạo ra
     */
    public AudioStory(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }
    /**
     * Getter để lấy đường dẫn file âm thanh.
     *
     * @return đường dẫn file âm thanh
     */
    public String getAudioFilePath() {
        return audioFilePath;
    }
    /**
     * Setter để cập nhật đường dẫn file âm thanh.
     */
    public void setAudioFilePath(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }
}
