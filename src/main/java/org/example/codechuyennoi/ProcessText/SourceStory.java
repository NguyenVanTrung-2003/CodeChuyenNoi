package org.example.codechuyennoi.ProcessText;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lớp SourceStory chịu trách nhiệm tải nội dung HTML từ URL nguồn truyện.
 * Sử dụng thư viện Jsoup để gửi request HTTP và nhận về HTML của trang web.
 */
@Component
public class SourceStory {
    private static final Logger logger = LoggerFactory.getLogger(SourceStory.class);

    // URL nguồn chứa nội dung truyện cần tải về
    private final String urlSource;

    /**
     * Constructor khởi tạo SourceStory với URL nguồn.
     * @param urlSource URL của trang web chứa truyện
     */
    public SourceStory(String urlSource) {
        this.urlSource = urlSource;
    }
    /**
     * Phương thức fetchHtmlContent gửi yêu cầu HTTP GET tới URL nguồn,
     * tải nội dung HTML thô của trang truyện.
     * @return Chuỗi HTML nếu tải thành công, null nếu lỗi hoặc không tải được
     */
    public String fetchHtmlContent() {
        try {
            logger.info("Đang tải HTML từ: {}", urlSource);

            // Sử dụng Jsoup kết nối tới URL với userAgent để tránh bị chặn
            // Thiết lập timeout 10 giây
            return Jsoup.connect(urlSource)
                    .userAgent("Mozilla/5.0")  // Giả lập trình duyệt phổ biến
                    .timeout(10000)            // Timeout 10 giây
                    .get()                    // Thực hiện GET request
                    .html();                  // Lấy nội dung HTML dưới dạng chuỗi
        } catch (Exception e) {

            logger.error("Lỗi khi tải HTML: {}", e.getMessage(), e);
            return null;
        }
    }
}
