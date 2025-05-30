package org.example.codechuyennoi.Integation;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;

import org.example.codechuyennoi.ProcessVideo.VideoStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

 // Lớp YouTubeUploader xử lý việc xác thực và tải video lên YouTube

public class YouTubeUploader {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeUploader.class);

    private static final String APPLICATION_NAME = "StoryProcessor"; // Tên ứng dụng
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance(); // Factory xử lý JSON
    private final YouTube youTube; // Đối tượng YouTube dùng để gọi API

    /** Constructor: Khởi tạo YouTube client với xác thực OAuth2
     * @param clientSecretPath Đường dẫn đến file client_secret.json của Google
     */
    public YouTubeUploader(String clientSecretPath) {
        try {
            var httpTransport = GoogleNetHttpTransport.newTrustedTransport(); // Giao thức HTTP bảo mật
            var credential = authorize(httpTransport, clientSecretPath); // Xác thực OAuth2

            this.youTube = new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build(); // Tạo YouTube API client
        } catch (Exception e) {
            logger.error("Lỗi khi khởi tạo YouTube client: {}", e.getMessage());
            throw new RuntimeException("Không thể khởi tạo YouTube client", e);
        }
    }

    /**
     * Thực hiện xác thực OAuth2 với Google API để có quyền upload video
     * @param httpTransport Kết nối HTTP
     * @param clientSecretPath Đường dẫn đến file client_secret.json
     * @return Credential sau khi xác thực
     */
    private Credential authorize(com.google.api.client.http.HttpTransport httpTransport, String clientSecretPath) throws Exception {
        // Đọc thông tin client từ file JSON
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, new FileReader(clientSecretPath)
        );

        // Chỉ định quyền cần thiết: upload video lên YouTube
        List<String> scopes = Collections.singletonList("https://www.googleapis.com/auth/youtube.upload");

        // Tạo luồng xác thực OAuth
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, scopes
        ).setAccessType("offline").build(); // offline để lấy refresh_token

        // Tạo LocalServerReceiver để mở trình duyệt xác thực OAuth
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        // Thực hiện xác thực và trả về credential
        return new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");
    }

    /**
     * Tải video lên YouTube dựa trên thông tin videoStory và metadata
     * @param videoStory Đối tượng chứa đường dẫn video
     * @param metadata Mô tả thêm cho video
     * @return videoId nếu thành công, null nếu lỗi
     */
    public String uploadVideo(VideoStory videoStory, String metadata) {
        if (videoStory == null || videoStory.getVideoFilePath() == null) {
            logger.warn("File video rỗng");
            return null;
        }

        try {
            logger.info("Đang tải video lên YouTube: {}", videoStory.getVideoFilePath());

            // Tạo đối tượng Video metadata
            Video videoObject = new Video();

            // Thiết lập trạng thái (công khai)
            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus("public");
            videoObject.setStatus(status);

            // Thiết lập tiêu đề, mô tả, thẻ tag
            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle(videoStory.getTitle()); // Sử dụng tiêu đề từ VideoStory
            snippet.setDescription(metadata);
            snippet.setTags(List.of("story", "ai", "generated"));
            videoObject.setSnippet(snippet);

            // Chuẩn bị nội dung video
            File mediaFile = new File(videoStory.getVideoFilePath());
            InputStreamContent mediaContent = new InputStreamContent(
                    "video/*", Files.newInputStream(mediaFile.toPath())
            );

            // Tạo yêu cầu upload video
            YouTube.Videos.Insert videoInsert = youTube.videos()
                    .insert("snippet,status", videoObject, mediaContent);

            // Tải video dạng chunk
            videoInsert.getMediaHttpUploader().setDirectUploadEnabled(false);

            // Gửi yêu cầu và nhận videoId
            Video returnedVideo = videoInsert.execute();
            String videoId = returnedVideo.getId();

            logger.info("Đã tải video lên YouTube, ID: {}", videoId);
            return videoId;

        } catch (Exception e) {
            logger.error("Lỗi khi tải video lên YouTube: {}", e.getMessage());
            return null;
        }
    }
}
