package org.example.codechuyennoi.Notification;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChapterMonitor implements Runnable {
    private final BlockingQueue<Integer> chapterQueue;
    private volatile int lastKnownChapter;
    private final String baseUrl;
    private static final Logger logger = LoggerFactory.getLogger(ChapterMonitor.class);
    private final File lastChapterFile;
    private final String storyName;

    public ChapterMonitor(BlockingQueue<Integer> chapterQueue, String baseUrl, String lastChapterFilePath, String storyName) {
        this.chapterQueue = chapterQueue;
        this.baseUrl = baseUrl;
        String lastChapterPath = "luutrutruyen/" + storyName + "/lastChapter.txt";
        this.lastChapterFile = new File(lastChapterPath);
        this.storyName = storyName;
        this.lastKnownChapter = loadLastKnownChapter();
        logger.info("Khởi động ChapterMonitor với lastKnownChapter = {}", lastKnownChapter);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                logger.info("🕓 [{}] Đang kiểm tra chương mới tại: {}", now, baseUrl);

                int newestChapter = checkNewestChapterFromSource();
                if (newestChapter == -1) {
                    logger.warn("⚠️ Không thể lấy thông tin chương mới (có thể lỗi mạng hoặc selector). Giữ lastKnownChapter: {}", lastKnownChapter);
                } else if (newestChapter > lastKnownChapter) {
                    logger.info("📢 Phát hiện chương mới: {} (lớn hơn lastKnownChapter {})", newestChapter, lastKnownChapter);

                    for (int ch = lastKnownChapter + 1; ch <= newestChapter; ch++) {
                        if (!chapterFileExists(ch)) {
                            chapterQueue.put(ch);
                            logger.info("📥 Thêm chương mới vào hàng đợi: {}", ch);
                        } else {
                            logger.info("Producer: chương {} đã có file, bỏ qua.", ch);
                        }
                    }
                    updateLastKnownChapter(newestChapter);

                } else {
                    logger.info("✅ Không có chương mới. lastKnownChapter vẫn là {}", lastKnownChapter);
                    // 🆕 Kiểm tra lại 5 chương gần nhất đề phòng bị mất file
                    int startCheck = Math.max(lastKnownChapter - 5 + 1, 1);
                    for (int ch = startCheck; ch <= lastKnownChapter; ch++) {
                        if (!chapterFileExists(ch)) {
                            chapterQueue.put(ch);
                            logger.info("♻️ File chương {} bị thiếu. Đã đưa lại vào hàng đợi để xử lý lại.", ch);
                        }
                    }
                }

                Thread.sleep(5 * 60 * 1000); // ngủ 5 phút
            } catch (InterruptedException e) {
                logger.info("ChapterMonitor bị dừng bởi interrupt.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("❌ Lỗi khi kiểm tra chương mới:", e);
            }
        }
    }

    private int checkNewestChapterFromSource() {
        try {
            Document doc = Jsoup.connect(baseUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            Elements chapterLinks = doc.select("a[href*=/chuong-]");
            if (chapterLinks.isEmpty()) {
                logger.warn("Không tìm thấy link chương nào tại nguồn: {}", baseUrl);
                return -1;
            }

            return chapterLinks.stream()
                    .map(link -> extractChapterNumber(link.attr("href")))
                    .filter(num -> num > 0)
                    .max(Integer::compare)
                    .orElse(-1);

        } catch (IOException e) {
            logger.error("Lỗi khi kết nối hoặc xử lý trang nguồn: {}", baseUrl, e);
            return -1;
        }
    }

    private int extractChapterNumber(String href) {
        try {
            Pattern pattern = Pattern.compile("chuong-(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(href);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            logger.error("Lỗi khi trích xuất số chương từ href: {}", href, e);
        }
        return -1;
    }

    private int loadLastKnownChapter() {
        if (!lastChapterFile.exists()) {
            logger.info("File lưu lastKnownChapter không tồn tại, bắt đầu từ 0.");
            return 0;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(lastChapterFile))) {
            String line = reader.readLine();
            if (line != null) {
                int chapter = Integer.parseInt(line.trim());
                logger.info("Đã đọc lastKnownChapter từ file: {}", chapter);
                return chapter;
            }
        } catch (Exception e) {
            logger.error("Lỗi khi đọc lastKnownChapter từ file:", e);
        }
        return 0;
    }

    private void updateLastKnownChapter(int newChapter) {
        logger.info("Cập nhật lastKnownChapter: {} -> {}", lastKnownChapter, newChapter);
        lastKnownChapter = newChapter;
        saveLastKnownChapter(newChapter);
    }

    private void saveLastKnownChapter(int chapter) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(lastChapterFile, false))) {
            writer.write(String.valueOf(chapter));
            logger.info("Đã lưu lastKnownChapter vào file: {}", chapter);
        } catch (IOException e) {
            logger.error("Lỗi khi lưu lastKnownChapter vào file:", e);
        }
    }

    private boolean chapterFileExists(int chapter) {
        String dirPath = "luutrutruyen/" + storyName;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                logger.error("Không thể tạo thư mục: {}", dirPath);
            }
        }
        String filePath = dirPath + "/chuong-" + chapter + ".txt";
        File chapterFile = new File(filePath);
        boolean exists = chapterFile.exists() && chapterFile.length() > 0;
        logger.debug("Kiểm tra chương {}: filePath={}, tồn tại và không rỗng = {}", chapter, filePath, exists);
        return exists;
    }
}
