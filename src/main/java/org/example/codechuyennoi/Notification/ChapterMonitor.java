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

/**
 * ChapterMonitor là một lớp theo dõi chương mới từ một trang web truyện.
 * Khi phát hiện chương mới, nó thêm số chương vào hàng đợi để xử lý tiếp.
 */
public class ChapterMonitor implements Runnable {
    private final BlockingQueue<Integer> chapterQueue; // Hàng đợi để đưa chương mới vào xử lý
    private volatile int lastKnownChapter; // Chương cuối cùng đã được xử lý
    private final String baseUrl; // Đường dẫn đến danh sách chương của truyện
    private static final Logger logger = LoggerFactory.getLogger(ChapterMonitor.class);
    private final File lastChapterFile; // File lưu chương cuối đã xử lý
    private final String storyName; // Tên truyện (dùng để tạo thư mục lưu trữ riêng)

    /**
     * Constructor: Khởi tạo đối tượng ChapterMonitor
     * @param chapterQueue Hàng đợi để chứa chương mới
     * @param baseUrl Đường dẫn đến danh sách chương
     * @param lastChapterFilePath Không dùng nữa, giữ cho tương thích
     * @param storyName Tên truyện (dùng làm tên thư mục)
     */
    public ChapterMonitor(BlockingQueue<Integer> chapterQueue, String baseUrl, String lastChapterFilePath, String storyName) {
        this.chapterQueue = chapterQueue;
        this.baseUrl = baseUrl;
        this.storyName = storyName;
        String lastChapterPath = "luutrutruyen/" + storyName + "/lastChapter.txt"; // Tạo đường dẫn file lưu chương cuối
        this.lastChapterFile = new File(lastChapterPath);
        this.lastKnownChapter = loadLastKnownChapter(); // Đọc chương cuối từ file (nếu có)
        logger.info("Khởi động ChapterMonitor với lastKnownChapter = {}", lastKnownChapter);
    }

    /**
     * Phương thức chạy khi thread được khởi động
     * Theo dõi chương mới định kỳ mỗi 5 phút
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                logger.info("🕓 [{}] Đang kiểm tra chương mới tại: {}", now, baseUrl);

                int newestChapter = checkNewestChapterFromSource(); // Kiểm tra chương mới nhất từ trang web

                if (newestChapter == -1) {
                    handleErrorFetchingChapter(); // Không thể lấy dữ liệu (lỗi mạng, selector...)
                } else if (newestChapter > lastKnownChapter) {
                    handleNewChaptersFound(newestChapter); // Phát hiện có chương mới
                } else {
                    handleNoNewChapters(); // Không có chương mới, kiểm tra chương bị thiếu
                }

                Thread.sleep(15 * 60 * 1000); // Ngủ 5 phút trước khi kiểm tra tiếp
            } catch (InterruptedException e) {
                logger.info("ChapterMonitor bị dừng bởi interrupt.");
                Thread.currentThread().interrupt(); // Đảm bảo dừng đúng cách
            } catch (Exception e) {
                logger.error("❌ Lỗi khi kiểm tra chương mới:", e);
            }
        }
    }

    /**
     * Xử lý khi không thể lấy chương mới (do lỗi mạng, lỗi selector,...)
     */
    private void handleErrorFetchingChapter() {
        logger.warn("⚠️ Không thể lấy thông tin chương mới (có thể lỗi mạng hoặc selector). Giữ lastKnownChapter: {}", lastKnownChapter);
    }

    /**
     * Xử lý khi phát hiện chương mới so với lastKnownChapter
     */
    private void handleNewChaptersFound(int newestChapter) throws InterruptedException {
        logger.info("📢 Phát hiện chương mới: {} (lớn hơn lastKnownChapter {})", newestChapter, lastKnownChapter);

        for (int ch = lastKnownChapter + 1; ch <= newestChapter; ch++) {
            if (!chapterFileExists(ch)) { // Nếu file chương chưa tồn tại
                chapterQueue.put(ch); // Đưa vào hàng đợi xử lý
                logger.info("📥 Thêm chương mới vào hàng đợi: {}", ch);
            } else {
                logger.info("Producer: chương {} đã có file, bỏ qua.", ch);
            }
        }
        updateLastKnownChapter(newestChapter); // Cập nhật lastKnownChapter
    }

    /**
     * Xử lý khi không có chương mới: kiểm tra xem có chương cũ nào bị mất file không
     */
    private void handleNoNewChapters() throws InterruptedException {
        logger.info("✅ Không có chương mới. lastKnownChapter vẫn là {}", lastKnownChapter);

        int startCheck = Math.max(lastKnownChapter - 5 + 1, 1); // Kiểm tra 5 chương gần nhất
        boolean updated = false;

        for (int ch = startCheck; ch <= lastKnownChapter; ch++) {
            if (!chapterFileExists(ch)) { // Nếu chương bị mất file
                chapterQueue.put(ch); // Đưa lại vào hàng đợi
                logger.info("♻️ File chương {} bị thiếu. Đã đưa lại vào hàng đợi để xử lý lại.", ch);
            } else {
                if (ch == lastKnownChapter && !updated) {
                    updateLastKnownChapter(ch); // Cập nhật lại nếu cần
                    updated = true;
                }
            }
        }
    }

    /**
     * Kết nối tới trang web và lấy số chương mới nhất
     */
    private int checkNewestChapterFromSource() {
        try {
            Document doc = Jsoup.connect(baseUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get(); // Tải trang web

            Elements chapterLinks = doc.select("a[href*=/chuong-]"); // Chọn các thẻ a chứa chuong-
            if (chapterLinks.isEmpty()) {
                logger.warn("Không tìm thấy link chương nào tại nguồn: {}", baseUrl);
                return -1;
            }

            // Trích ra số chương từ từng link, chọn chương lớn nhất
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
    /**
     * Trích xuất số chương từ URL có dạng /chuong-123.html
     */
    private int extractChapterNumber(String href) {
        try {
            Pattern pattern = Pattern.compile("chuong-(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(href);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1)); // Lấy số chương từ group(1)
            }
        } catch (Exception e) {
            logger.error("Lỗi khi trích xuất số chương từ href: {}", href, e);
        }
        return -1;
    }

    /**
     * Đọc chương cuối cùng đã lưu từ file lastChapter.txt
     */
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

    /**
     * Cập nhật lastKnownChapter mới và ghi vào file
     */
    private void updateLastKnownChapter(int newChapter) {
        logger.info("Cập nhật lastKnownChapter: {} -> {}", lastKnownChapter, newChapter);
        lastKnownChapter = newChapter;
        saveLastKnownChapter(newChapter);
    }

    /**
     * Ghi chương cuối vào file lastChapter.txt
     */
    private void saveLastKnownChapter(int chapter) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(lastChapterFile, false))) {
            writer.write(String.valueOf(chapter));
            logger.info("Đã lưu lastKnownChapter vào file: {}", chapter);
        } catch (IOException e) {
            logger.error("Lỗi khi lưu lastKnownChapter vào file:", e);
        }
    }

    /**
     * Kiểm tra chương đã được lưu file chưa và file có nội dung không
     */
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
        boolean exists = chapterFile.exists() && chapterFile.length() > 0; // Tồn tại và không rỗng
        logger.debug("Kiểm tra chương {}: filePath={}, tồn tại và không rỗng = {}", chapter, filePath, exists);
        return exists;
    }
}
