package com.telegrambusiness.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Controller
public class RenderKeepAliveService {

    private static final String RENDER_URL = "https://bot-ehk9.onrender.com";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @GetMapping("/")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @Scheduled(fixedRate = 300_000)
    public void pingRender() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RENDER_URL))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("Render keep-alive ping: status={}", response.statusCode());
        } catch (Exception e) {
            log.warn("Render keep-alive ping failed: {}", e.getMessage());
        }
    }
}
