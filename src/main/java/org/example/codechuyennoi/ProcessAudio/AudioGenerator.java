package org.example.codechuyennoi.ProcessAudio;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class AudioGenerator {
    private static final String DEMO_TTS_URL = "https://speech.aiservice.vn/tts/tools/demo";
    private static final String CHROMEDRIVER_PATH = "D:\\WebApi\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe";

    public AudioStory generateAudio(String storyName, int chapterNumber, String processedText) {
        if (processedText == null || processedText.trim().isEmpty()) {
            System.out.println("Văn bản đầu vào trống.");
            return null;
        }

        try {
            // Tạo thư mục đầu ra
            String outputDirPath = "output/" + storyName;
            Path outputDir = Paths.get(outputDirPath).toAbsolutePath();
            Files.createDirectories(outputDir);

            // Xoá các file tạm trước khi bắt đầu
            deleteCrdownloadFiles(outputDir);

            // Cấu hình trình duyệt
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", outputDir.toString());
            prefs.put("profile.default_content_settings.popups", 0);

            ChromeOptions options = new ChromeOptions();
            options.setExperimentalOption("prefs", prefs);
            System.setProperty("webdriver.chrome.driver", CHROMEDRIVER_PATH);
            WebDriver driver = new ChromeDriver(options);

            try {
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                driver.get(DEMO_TTS_URL);

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
                WebElement textArea = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("edit-content")));

                // Dán văn bản vào textarea
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].value = arguments[1];" +
                                "arguments[0].dispatchEvent(new Event('input'));" +
                                "arguments[0].dispatchEvent(new Event('change'));" +
                                "arguments[0].dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));",
                        textArea, processedText
                );
                Thread.sleep(1000);

                // Chọn giọng đọc
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//select[@id='voice']")));
                WebElement voiceSelect = driver.findElement(By.xpath("//select[@id='voice']"));
                new Select(voiceSelect).selectByIndex(1); // Giọng thứ 2

                // Lưu URL cũ
                WebElement audioElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("audio")));
                String oldAudioUrl = audioElement.getAttribute("src");

// Bấm nút Tạo file
                WebElement downloadButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("submit_btn")));
                downloadButton.click();
                System.out.println("Đã bấm nút Tạo file.");

// Đợi src mới
                WebDriverWait waitForAudio = new WebDriverWait(driver, Duration.ofSeconds(30));
                waitForAudio.until(driver1 -> {
                    WebElement audio = driver1.findElement(By.id("audio"));
                    String newSrc = audio.getAttribute("src");
                    return newSrc != null && !newSrc.isEmpty() && !newSrc.equals(oldAudioUrl);
                });

// Lấy URL mới
                WebElement audioElementNew = driver.findElement(By.id("audio"));
                String audioUrl = audioElementNew.getAttribute("src");


                // Tên file đầu ra
                String outputFileName = "audio_chuong_" + chapterNumber + ".m4a";
                Path finalPath = outputDir.resolve(outputFileName);

                // Tải file từ URL
                try (InputStream in = new URL(audioUrl).openStream()) {
                    Files.copy(in, finalPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Đã tải xong file: " + finalPath);
                    System.out.println("Kích thước file: " + Files.size(finalPath));
                    return new AudioStory(finalPath.toString());
                } catch (IOException e) {
                    System.err.println("Lỗi khi tải file âm thanh: " + e.getMessage());
                    return null;
                }

            } finally {
                driver.quit();
            }

        } catch (Exception e) {
            System.err.println("Lỗi khi tạo âm thanh: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Xoá file *.crdownload còn tồn tại
    private void deleteCrdownloadFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.crdownload")) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
    }
}
