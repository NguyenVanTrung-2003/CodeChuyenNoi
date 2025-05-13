package org.example.codechuyennoi;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class YouTubeUploader {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeUploader.class);
    private static final String APPLICATION_NAME = "StoryProcessor";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private final YouTube youTube;

    public YouTubeUploader(String clientSecretPath) {
        try {
            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            var credential = authorize(httpTransport, clientSecretPath);

            this.youTube = new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            logger.error("Lỗi khi khởi tạo YouTube client: {}", e.getMessage());
            throw new RuntimeException("Không thể khởi tạo YouTube client", e);
        }
    }

    private Credential authorize(com.google.api.client.http.HttpTransport httpTransport, String clientSecretPath) throws Exception {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, new FileReader(clientSecretPath)
        );

        List<String> scopes = Collections.singletonList("https://www.googleapis.com/auth/youtube.upload");

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, scopes
        ).setAccessType("offline").build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        return new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");
    }

    public String uploadVideo(VideoStory videoStory, String metadata) {
        if (videoStory == null || videoStory.getVideoFilePath() == null) {
            logger.warn("File video rỗng");
            return null;
        }
        try {
            logger.info("Đang tải video lên YouTube: {}", videoStory.getVideoFilePath());

            // Tạo metadata cho video
            Video videoObject = new Video();

            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus("public"); // hoặc unlisted/private
            videoObject.setStatus(status);

            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle("Auto-generated Story Video");
            snippet.setDescription(metadata);
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
            logger.error("Lỗi khi tải video lên YouTube: {}", e.getMessage());
            return null;
        }
    }
}
