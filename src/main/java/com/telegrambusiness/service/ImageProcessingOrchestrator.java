package com.telegrambusiness.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingOrchestrator {

    private final OpenAiVisionService openAiVisionService;
    private final GoogleDriveService googleDriveService;

    @Async
    public CompletableFuture<String> processImage(byte[] imageBytes, String originalFileName) {
        try {
            log.info("Processing image '{}'", originalFileName);

            String date = openAiVisionService.extractDateFromImage(imageBytes);
            log.info("Detected date: {}", date);

            String[] parts = date.split("\\.");
            String day = parts[0];
            String month = parts[1];

            String extension = "";
            int dotIndex = originalFileName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalFileName.substring(dotIndex);
            }
            String basePrefix = "file_" + day + "_" + month;

            GoogleDriveService.UploadResult result = googleDriveService.uploadImage(imageBytes, basePrefix, extension, month);

            String folder = Year.now().getValue() + "-" + month;
            return CompletableFuture.completedFuture(
                    "Done! Date: " + date + ", uploaded as " + result.fileName() + " to /sales/" + folder + " (fileId: " + result.fileId() + ")");
        } catch (Exception e) {
            log.error("Failed to process image '{}'", originalFileName, e);
            return CompletableFuture.completedFuture("Error processing image: " + e.getMessage());
        }
    }
}
