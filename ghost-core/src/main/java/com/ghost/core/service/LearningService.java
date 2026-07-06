package com.ghost.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class LearningService {

    private final MemoryService memoryService;

    @Async
    public void analyzeAndLearn(String userMessage, String aiResponse, String firebaseUid) {
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }

        String lower = userMessage.toLowerCase(Locale.ROOT);
        boolean shouldSave = lower.contains("me chame")
                || lower.contains("prefiro")
                || lower.contains("lembre")
                || lower.contains("guarde")
                || lower.contains("meu projeto")
                || lower.contains("meu pc")
                || lower.contains("meu relogio")
                || lower.contains("smartwatch")
                || lower.contains("notion")
                || lower.contains("obsidian")
                || lower.contains("ollama");

        if (!shouldSave) {
            return;
        }

        String summary = "Usuario informou: " + userMessage.trim();
        if (summary.length() > 900) {
            summary = summary.substring(0, 900) + "...";
        }

        try {
            memoryService.saveMemory(summary, firebaseUid, "operator-context", 7);
        } catch (Exception e) {
            log.warn("Autoaprendizado local indisponivel: {}", e.getMessage());
        }
    }
}
