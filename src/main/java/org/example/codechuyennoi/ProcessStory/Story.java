package org.example.codechuyennoi.ProcessStory;

import java.util.List;

@lombok.Data // Tự động sinh getter, setter, equals, hashCode, toString, constructor mặc định
public class Story {

    private int chapterNumber;
    // Văn bản đã xử lý (đã được làm sạch, chuẩn hóa...)
    private String processedText;

    private String storyName;

     // Constructor khởi tạo Story với tên truyện, số chương và văn bản đã xử lý.

    public Story(String storyName, int chapterNumber, String processedText) {
        this.processedText = processedText;
        this.chapterNumber = chapterNumber;
        this.storyName = storyName;
    }
    /**
     * Trích xuất danh sách các câu từ processedText.
     * Dựa trên dấu kết thúc câu: '.', '!', '?' và khoảng trắng sau đó.
     *
     * @return danh sách các câu, hoặc danh sách rỗng nếu processedText rỗng
     */
    public List<String> getSentences() {
        if (processedText == null || processedText.isBlank()) return List.of();
        return List.of(processedText.split("(?<=[.!?])\\s+")); // Tách câu theo dấu câu kết thúc
    }
}
