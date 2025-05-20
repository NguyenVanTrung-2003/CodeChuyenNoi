package org.example.codechuyennoi.Notification;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;

public class ChapterMonitor implements Runnable {
    private final BlockingQueue<Integer> chapterQueue;
    private volatile int lastKnownChapter;
    private final String baseUrl;
    private static final Logger logger = LoggerFactory.getLogger(ChapterMonitor.class);

    public ChapterMonitor(BlockingQueue<Integer> chapterQueue, int initialLastChapter, String baseUrl) {
        this.chapterQueue = chapterQueue;
        this.lastKnownChapter = initialLastChapter;
        this.baseUrl = baseUrl;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    logger.info("🕓 [{}] Đang kiểm tra chương mới tại: {}", now, baseUrl);

                    int newestChapter = checkNewestChapterFromSource();

                    if (newestChapter == -1) {
                        logger.warn("⚠️ Không thể lấy thông tin chương mới (có thể lỗi mạng hoặc selector).");
                        Thread.sleep(5 * 60 * 1000); // nghỉ 5 phút rồi thử lại
                        continue; // bỏ qua lần chạy này
                    }

                    if (newestChapter > lastKnownChapter) {
                        for (int ch = lastKnownChapter + 1; ch <= newestChapter; ch++) {
                            chapterQueue.put(ch);
                            logger.info("📥 Thêm chương mới vào hàng đợi: {}", ch);
                        }
                        lastKnownChapter = newestChapter;
                    } else {
                        logger.info("✅ Không có chương mới. Vẫn là chương {}", lastKnownChapter);
                    }

                    Thread.sleep(5 * 60 * 1000); // ngủ 5 phút
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("❌ Lỗi khi kiểm tra chương mới:", e);
                }
            }
    }
        }
    //...

    private int checkNewestChapterFromSource() {
        try {
            Document doc = Jsoup.connect(baseUrl).get();

            Elements chapterLinks = doc.select("div.list-chapter a");  // kiểm tra lại selector với trang thực tế

            int maxChapter = 0;
            for (Element link : chapterLinks) {
                String href = link.attr("href"); // ví dụ: /thay-phong-thuy/chuong-20/
                int chapterNum = extractChapterNumber(href);
                if (chapterNum > maxChapter) {
                    maxChapter = chapterNum;
                }
            }
            return maxChapter;

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private int extractChapterNumber(String href) {
        try {
            int idx = href.indexOf("chuong-");
            if (idx >= 0) {
                String part = href.substring(idx + 7);
                part = part.replaceAll("[^0-9]", "");
                return Integer.parseInt(part);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
