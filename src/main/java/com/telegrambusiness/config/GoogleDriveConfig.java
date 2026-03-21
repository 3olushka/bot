package com.telegrambusiness.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.List;

@Slf4j
@Configuration
public class GoogleDriveConfig {

    @Value("${google.drive.credentials-path}")
    private String credentialsPath;

    @Value("${google.drive.tokens-path}")
    private String tokensPath;

    @Value("${google.drive.refresh-token:}")
    private String refreshToken;

    private final ResourceLoader resourceLoader;

    public GoogleDriveConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public Drive driveService() throws IOException, GeneralSecurityException {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();

        var reader = new InputStreamReader(
                resourceLoader.getResource(credentialsPath).getInputStream());
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);

        Credential credential;

        if (refreshToken != null && !refreshToken.isBlank()) {
            // Server mode: use refresh token from env var
            credential = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .setClientSecrets(clientSecrets)
                    .build()
                    .setRefreshToken(refreshToken);
            log.info("Using refresh token from environment variable");
        } else {
            // Local mode: browser-based OAuth flow
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory, clientSecrets, List.of(DriveScopes.DRIVE_FILE))
                    .setDataStoreFactory(new FileDataStoreFactory(new File(tokensPath)))
                    .setAccessType("offline")
                    .build();

            credential = flow.loadCredential("user");
            if (credential == null || (credential.getRefreshToken() == null
                    && credential.getExpiresInSeconds() != null
                    && credential.getExpiresInSeconds() <= 0)) {
                var receiver = new LocalServerReceiver.Builder().setPort(8888).build();
                credential = new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
            }

            if (credential.getRefreshToken() != null) {
                log.info("=== REFRESH TOKEN (save this for server deployment) ===");
                log.info("{}", credential.getRefreshToken());
                log.info("=== END REFRESH TOKEN ===");
            }
        }

        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("telegram-business")
                .build();
    }
}
