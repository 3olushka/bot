package com.telegrambusiness.bot;

import com.telegrambusiness.service.ImageProcessingOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URI;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class ImageBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String botToken;
    private final TelegramClient telegramClient;
    private final ImageProcessingOrchestrator orchestrator;

    public ImageBot(@Value("${telegram.bot.token}") String botToken,
                    ImageProcessingOrchestrator orchestrator) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.orchestrator = orchestrator;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    private static final List<String> IMAGE_MIME_PREFIXES = List.of("image/");

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        if (update.getMessage().hasPhoto()) {
            handlePhoto(update);
        } else if (update.getMessage().hasDocument()) {
            Document doc = update.getMessage().getDocument();
            if (doc.getMimeType() != null && IMAGE_MIME_PREFIXES.stream().anyMatch(doc.getMimeType()::startsWith)) {
                handleDocument(update);
            }
        }
    }

    private void handlePhoto(Update update) {
        long chatId = update.getMessage().getChatId();
        try {
            List<PhotoSize> photos = update.getMessage().getPhoto();
            PhotoSize largest = photos.stream()
                    .max(Comparator.comparingInt(PhotoSize::getFileSize))
                    .orElseThrow(() -> new RuntimeException("No photo found"));

            GetFile getFile = new GetFile(largest.getFileId());
            org.telegram.telegrambots.meta.api.objects.File telegramFile = telegramClient.execute(getFile);

            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + telegramFile.getFilePath();
            byte[] imageBytes;
            try (InputStream is = URI.create(fileUrl).toURL().openStream()) {
                imageBytes = is.readAllBytes();
            }

            String fileName = telegramFile.getFilePath().substring(
                    telegramFile.getFilePath().lastIndexOf('/') + 1);

            orchestrator.processImage(imageBytes, fileName)
                    .thenAccept(result -> sendText(chatId, result))
                    .exceptionally(ex -> {
                        log.error("Async processing failed for chat {}", chatId, ex);
                        sendText(chatId, "Error: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error handling photo for chat {}", chatId, e);
            sendText(chatId, "Error: " + e.getMessage());
        }
    }

    private void handleDocument(Update update) {
        long chatId = update.getMessage().getChatId();
        try {
            Document doc = update.getMessage().getDocument();

            GetFile getFile = new GetFile(doc.getFileId());
            org.telegram.telegrambots.meta.api.objects.File telegramFile = telegramClient.execute(getFile);

            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + telegramFile.getFilePath();
            byte[] imageBytes;
            try (InputStream is = URI.create(fileUrl).toURL().openStream()) {
                imageBytes = is.readAllBytes();
            }

            String fileName = doc.getFileName() != null ? doc.getFileName() : telegramFile.getFilePath().substring(
                    telegramFile.getFilePath().lastIndexOf('/') + 1);

            orchestrator.processImage(imageBytes, fileName)
                    .thenAccept(result -> sendText(chatId, result))
                    .exceptionally(ex -> {
                        log.error("Async processing failed for chat {}", chatId, ex);
                        sendText(chatId, "Error: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error handling document for chat {}", chatId, e);
            sendText(chatId, "Error: " + e.getMessage());
        }
    }

    private void sendText(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }
}
