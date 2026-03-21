package com.telegrambusiness.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
public class GoogleDriveConfig {

    @Value("${google.drive.credentials-path}")
    private String credentialsPath;

    @Value("${google.drive.tokens-path}")
    private String tokensPath;

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

        File tokensDir = prepareTokensDir();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, List.of(DriveScopes.DRIVE_FILE))
                .setDataStoreFactory(new FileDataStoreFactory(tokensDir))
                .setAccessType("offline")
                .build();

        Credential credential = flow.loadCredential("user");
        if (credential == null || (credential.getRefreshToken() == null && credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 0)) {
            var receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            credential = new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }

        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("telegram-business")
                .build();
    }

    private File prepareTokensDir() throws IOException {
        Path source = Path.of(tokensPath);
        File sourceFile = source.toFile();

        // If tokens path is already writable, use it directly (local dev)
        if (sourceFile.isDirectory() && sourceFile.canWrite()) {
            return sourceFile;
        }

        // Copy StoredCredential to a writable /tmp directory (server/Render)
        Path tmpTokens = Path.of(System.getProperty("java.io.tmpdir"), "google-tokens");
        Files.createDirectories(tmpTokens);

        // tokensPath might be a directory containing StoredCredential, or the file itself
        Path storedCredential = sourceFile.isDirectory()
                ? source.resolve("StoredCredential")
                : source;

        if (Files.exists(storedCredential)) {
            Files.copy(storedCredential, tmpTokens.resolve("StoredCredential"), StandardCopyOption.REPLACE_EXISTING);
        }

        return tmpTokens.toFile();
    }
}
