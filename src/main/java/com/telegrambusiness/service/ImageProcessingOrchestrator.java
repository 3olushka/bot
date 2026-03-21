package com.telegrambusiness.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingOrchestrator {

    private final OpenAiVisionService openAiVisionService;
    private final GoogleDriveService googleDriveService;

    public String processImage(byte[] imageBytes, String originalFileName) {
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
            String fileName = "file_" + day + "_" + month + extension;

            String fileId = googleDriveService.uploadImage(imageBytes, fileName, month);

            String folder = Year.now().getValue() + "-" + month;
            return "Done! Date: " + date + ", uploaded as " + fileName + " to /sales/" + folder + " (fileId: " + fileId + ")";
        } catch (Exception e) {
            log.error("Failed to process image '{}'", originalFileName, e);
            return "Error processing image: " + e.getMessage();
        }
    }
}
