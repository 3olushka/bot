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

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private final Drive driveService;

    @Value("${google.drive.root-folder-name}")
    private String rootFolderName;

    public String uploadImage(byte[] imageBytes, String fileName, String month) throws IOException {
        String salesFolderId = findOrCreateFolder(rootFolderName, null);
        String monthFolderName = Year.now().getValue() + "-" + month;
        String monthFolderId = findOrCreateFolder(monthFolderName, salesFolderId);

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(List.of(monthFolderId));

        ByteArrayContent content = new ByteArrayContent("image/jpeg", imageBytes);

        File uploaded = driveService.files().create(fileMetadata, content)
                .setFields("id, name")
                .execute();

        log.info("Uploaded file '{}' to Drive folder '{}', fileId={}", fileName, monthFolderName, uploaded.getId());
        return uploaded.getId();
    }

    private String findOrCreateFolder(String name, String parentId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("mimeType='application/vnd.google-apps.folder'");
        query.append(" and name='").append(name).append("'");
        query.append(" and trashed=false");
        if (parentId != null) {
            query.append(" and '").append(parentId).append("' in parents");
        }

        FileList result = driveService.files().list()
                .setQ(query.toString())
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute();

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

        File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

        log.info("Created folder '{}' with id={}", name, folder.getId());
        return folder.getId();
    }
}
