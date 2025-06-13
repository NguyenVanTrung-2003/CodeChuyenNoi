package org.example.codechuyennoi.ProcessText;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


public class SourceStory {
    private static final Logger logger = LoggerFactory.getLogger(SourceStory.class);

    private final String urlSource;
    public SourceStory( String urlSource) {
        this.urlSource = urlSource;
    }

    public String fetchHtmlContent() {
        try {
            logger.info("Đang tải HTML từ: {}", urlSource);
            return Jsoup.connect(urlSource)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get()
                    .html();
        } catch (Exception e) {
            logger.error("Lỗi khi tải HTML: {}", e.getMessage(), e);
            return null;
        }
    }
}
