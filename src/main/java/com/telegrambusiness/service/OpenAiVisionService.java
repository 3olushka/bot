package com.telegrambusiness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OpenAiVisionService {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(0?[1-9]|[12]\\d|3[01])\\.(0?[1-9]|1[0-2])\\b");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.base-url}")
    private String baseUrl;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens}")
    private int maxTokens;

    public OpenAiVisionService(@Qualifier("openAiRestTemplate") RestTemplate restTemplate,
                                ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @return date string in "DD.MM" format
     */
    public String extractDateFromImage(byte[] imageBytes) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:image/jpeg;base64," + base64Image;

        Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text",
                                        "text", "Find a date in D.MM or DD.MM format on this image. Return ONLY the date as day.month (e.g., 1.02, 05.01, 23.12). Nothing else."),
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", dataUri))
                        ))
                )
        );

        String url = baseUrl + "/chat/completions";
        String responseBody = postWithRetry(url, request);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText().trim();
            log.info("OpenAI Vision response: {}", content);

            Matcher matcher = DATE_PATTERN.matcher(content);
            if (matcher.find()) {
                String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
                String month = String.format("%02d", Integer.parseInt(matcher.group(2)));
                return day + "." + month;
            }
            throw new RuntimeException("Could not extract a valid DD.MM date from OpenAI response: " + content);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    private String postWithRetry(String url, Map<String, Object> request) {
        ResourceAccessException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restTemplate.postForObject(url, request, String.class);
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("OpenAI API call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw lastException;
    }
}
