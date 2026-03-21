package com.telegrambusiness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OpenAiVisionService {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(0[1-9]|[12]\\d|3[01])\\.(0[1-9]|1[0-2])\\b");

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
                                        "text", "Find a date in DD.MM format on this image. Return ONLY the date in DD.MM format (e.g., 05.01, 23.12). Nothing else."),
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", dataUri))
                        ))
                )
        );

        String url = baseUrl + "/chat/completions";
        String responseBody = restTemplate.postForObject(url, request, String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText().trim();
            log.info("OpenAI Vision response: {}", content);

            Matcher matcher = DATE_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group();
            }
            throw new RuntimeException("Could not extract a valid DD.MM date from OpenAI response: " + content);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }
}
