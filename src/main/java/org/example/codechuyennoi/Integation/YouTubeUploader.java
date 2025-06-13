package org.example.codechuyennoi.Integation;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.example.codechuyennoi.ProcessVideo.VideoStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

@Service
public class YouTubeUploader {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeUploader.class);
    private static final String APPLICATION_NAME = "StoryProcessor";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private YouTube youTube;

    @Value("${google.oauth.client.secret.path}")
    private String clientSecretPath;

    @PostConstruct
    public void init() {
        initYouTubeClient(clientSecretPath);
    }

    public void initYouTubeClient(String clientSecretPath) {
        try {
            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            var credential = authorize(httpTransport, clientSecretPath);
            this.youTube = new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            logger.error("Lỗi khi khởi tạo YouTube client: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể khởi tạo YouTube client", e);
        }
    }

    private Credential authorize(HttpTransport httpTransport, String clientSecretPath) throws Exception {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, new FileReader(clientSecretPath)
        );

        List<String> scopes = Collections.singletonList("https://www.googleapis.com/auth/youtube.upload");

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, scopes
        ).setAccessType("offline").build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver) {
            @Override
            protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) {
                try {
                    String url = authorizationUrl.build();
                    logger.warn("⚠️ Không hỗ trợ mở trình duyệt tự động. Vui lòng mở tay: {}", url);

                    String os = System.getProperty("os.name").toLowerCase();
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } else if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                    } else if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", url});
                    } else if (os.contains("nix") || os.contains("nux")) {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                    } else {
                        logger.warn("⚠️ Không thể mở URL tự động. Vui lòng copy thủ công: {}", url);
                    }
                } catch (Exception e) {
                    logger.error("❌ Lỗi khi cố mở trình duyệt: {}", e.getMessage(), e);
                }
            }
        };

        return app.authorize("user");

    }

    public String uploadVideo(VideoStory videoStory, String videoTitle, String videoDescription) {
        if (videoStory == null || videoStory.getVideoFilePath() == null) {
            logger.warn("File video rỗng");
            return null;
        }

        if (this.youTube == null) {
            logger.error("YouTube client chưa được khởi tạo.");
            return null;
        }

        try {
            logger.info("Đang tải video lên YouTube: {}", videoStory.getVideoFilePath());

            Video videoObject = new Video();
            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus("public");
            videoObject.setStatus(status);

            VideoSnippet snippet = new VideoSnippet();

            // Nếu không nhập, dùng tiêu đề và mô tả mặc định trong videoStory
            snippet.setTitle(videoTitle != null && !videoTitle.isBlank() ? videoTitle : videoStory.getTitle());
            snippet.setDescription(videoDescription != null && !videoDescription.isBlank() ? videoDescription : "");  // Hoặc videoStory.getDescription() nếu có

            snippet.setTags(List.of("story", "ai", "generated"));
            videoObject.setSnippet(snippet);

            File mediaFile = new File(videoStory.getVideoFilePath());
            InputStreamContent mediaContent = new InputStreamContent(
                    "video/*", Files.newInputStream(mediaFile.toPath())
            );

            YouTube.Videos.Insert videoInsert = youTube.videos()
                    .insert("snippet,status", videoObject, mediaContent);
            videoInsert.getMediaHttpUploader().setDirectUploadEnabled(false);

            Video returnedVideo = videoInsert.execute();
            String videoId = returnedVideo.getId();

            logger.info("Đã tải video lên YouTube, ID: {}", videoId);
            return videoId;

        } catch (Exception e) {
            logger.error("Lỗi khi tải video lên YouTube: {}", e.getMessage(), e);
            return null;
        }
    }

}

