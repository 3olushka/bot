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

    public String processImage(byte[] imageBytes, String fileName) {
        try {
            log.info("Processing image '{}'", fileName);

            String month = openAiVisionService.extractMonthFromImage(imageBytes);
            log.info("Detected month: {}", month);

            String fileId = googleDriveService.uploadImage(imageBytes, fileName, month);

            String folder = Year.now().getValue() + "-" + month;
            return "Done! Month: " + month + ", uploaded to /sales/" + folder + " (fileId: " + fileId + ")";
        } catch (Exception e) {
            log.error("Failed to process image '{}'", fileName, e);
            return "Error processing image: " + e.getMessage();
        }
    }
}
