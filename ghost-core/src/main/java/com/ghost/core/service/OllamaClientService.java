package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OllamaClientService {

    private final RestClient ollamaRestClient;
    private final String primaryModel;
    private final String fallbackModel;
    private final String visionModel;
    private final int contextWindow;
    private final double temperature;

    public OllamaClientService(
            @Qualifier("ollamaRestClient") RestClient ollamaRestClient,
            @Value("${ghost.ai.primary-model:llama3:8b}") String primaryModel,
            @Value("${ghost.ai.fallback-model:deepseek-r1:8b}") String fallbackModel,
            @Value("${ghost.ai.vision-model:}") String visionModel,
            @Value("${ghost.ai.context-window:4096}") int contextWindow,
            @Value("${ghost.ai.temperature:0.6}") double temperature) {
        this.ollamaRestClient = ollamaRestClient;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.visionModel = visionModel;
        this.contextWindow = contextWindow;
        this.temperature = temperature;
    }

    public String chatWithFallback(String systemPrompt, String userPrompt) {
        String response = chat(primaryModel, systemPrompt, userPrompt);
        if (hasText(response)) {
            return response;
        }

        if (!fallbackModel.equalsIgnoreCase(primaryModel)) {
            log.warn("Ollama model '{}' unavailable. Trying fallback '{}'.", primaryModel, fallbackModel);
            response = chat(fallbackModel, systemPrompt, userPrompt);
            if (hasText(response)) {
                return response;
            }
        }

        return null;
    }

    public String chatWithImage(String systemPrompt, String userPrompt, byte[] imageBytes) {
        if (!hasText(visionModel) || imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "model", visionModel,
                    "prompt", systemPrompt + "\n\n" + userPrompt,
                    "images", List.of(Base64.getEncoder().encodeToString(imageBytes)),
                    "stream", false,
                    "options", options()
            );

            Map<?, ?> response = ollamaRestClient.post()
                    .uri("/api/generate")
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            Object content = response != null ? response.get("response") : null;
            return content instanceof String text && hasText(text) ? text.trim() : null;
        } catch (Exception e) {
            log.warn("Ollama vision model '{}' failed: {}", visionModel, e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        try {
            ollamaRestClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String describeModels() {
        return "primary=" + primaryModel
                + ", fallback=" + fallbackModel
                + (hasText(visionModel) ? ", vision=" + visionModel : ", vision=disabled");
    }

    private String chat(String model, String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "stream", false,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "options", options()
            );

            Map<?, ?> response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
                return null;
            }

            Object content = message.get("content");
            return content instanceof String text && hasText(text) ? text.trim() : null;
        } catch (Exception e) {
            log.warn("Ollama model '{}' failed: {}", model, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> options() {
        return Map.of(
                "temperature", temperature,
                "num_ctx", contextWindow,
                "keep_alive", Duration.ofMinutes(20).toMinutes() + "m"
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
