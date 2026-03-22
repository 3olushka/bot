package com.telegrambusiness.service;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Year;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private final Drive driveService;

    @Value("${google.drive.root-folder-name}")
    private String rootFolderName;

    public record UploadResult(String fileId, String fileName) {}

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final ConcurrentHashMap<String, Object> folderLocks = new ConcurrentHashMap<>();

    public UploadResult uploadImage(byte[] imageBytes, String basePrefix, String extension, String month) throws IOException {
        String salesFolderId = findOrCreateFolder(rootFolderName, null);
        String monthFolderName = Year.now().getValue() + "-" + month;
        String monthFolderId = findOrCreateFolder(monthFolderName, salesFolderId);

        Object lock = folderLocks.computeIfAbsent(monthFolderId, k -> new Object());
        synchronized (lock) {
            String counter = findNextCounter(basePrefix, monthFolderId);
            String fileName = basePrefix + "_" + counter + extension;

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setParents(List.of(monthFolderId));

            ByteArrayContent content = new ByteArrayContent("image/jpeg", imageBytes);

            File uploaded = retry(() -> driveService.files().create(fileMetadata, content)
                    .setFields("id, name")
                    .execute());

            log.info("Uploaded file '{}' to Drive folder '{}', fileId={}", fileName, monthFolderName, uploaded.getId());
            return new UploadResult(uploaded.getId(), fileName);
        }
    }

    private String findNextCounter(String basePrefix, String folderId) throws IOException {
        String query = "name contains '" + basePrefix + "_' and '" + folderId + "' in parents and trashed=false";

        FileList result = retry(() -> driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(name)")
                .setPageSize(100)
                .execute());

        int maxCounter = -1;
        Pattern pattern = Pattern.compile(Pattern.quote(basePrefix) + "_(\\d{2})\\.");

        if (result.getFiles() != null) {
            for (File file : result.getFiles()) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.find()) {
                    int counter = Integer.parseInt(matcher.group(1));
                    if (counter > maxCounter) {
                        maxCounter = counter;
                    }
                }
            }
        }

        return String.format("%02d", maxCounter + 1);
    }

    private String findOrCreateFolder(String name, String parentId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("mimeType='application/vnd.google-apps.folder'");
        query.append(" and name='").append(name).append("'");
        query.append(" and trashed=false");
        if (parentId != null) {
            query.append(" and '").append(parentId).append("' in parents");
        }

        FileList result = retry(() -> driveService.files().list()
                .setQ(query.toString())
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute());

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.info("Found existing folder '{}' with id={}", name, folderId);
            return folderId;
        }

        File folderMetadata = new File();
        folderMetadata.setName(name);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        if (parentId != null) {
            folderMetadata.setParents(List.of(parentId));
        }

        File folder = retry(() -> driveService.files().create(folderMetadata)
                .setFields("id")
                .execute());

        log.info("Created folder '{}' with id={}", name, folder.getId());
        return folder.getId();
    }

    private <T> T retry(Callable<T> action) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.call();
            } catch (IOException e) {
                lastException = e;
                log.warn("Google Drive API call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            } catch (Exception e) {
                throw new IOException("Unexpected error during Drive API call", e);
            }
        }
        throw lastException;
    }
}
