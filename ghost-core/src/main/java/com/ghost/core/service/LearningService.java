package com.ghost.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class LearningService {

    private final MemoryService memoryService;
    private final ChatModel geminiChatModel;
    private final ChatModel groqChatModel;

    // CONSTRUTOR MANUAL: Obrigatório para @Qualifier funcionar corretamente sem ambiguidades
    public LearningService(
            MemoryService memoryService,
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel, // <--- Nome correto do bean
            @Qualifier("groqChatModel") ChatModel groqChatModel) {
        
        this.memoryService = memoryService;
        this.geminiChatModel = geminiChatModel;
        this.groqChatModel = groqChatModel;
    }

    @Async
    public void analyzeAndLearn(String userMessage, String aiResponse, String firebaseUid) {
        log.info("Iniciando auto-aprendizado para usuário: {}", firebaseUid);

        String evaluationPrompt = """
            Você é o módulo de memória do GHOST.
            Analise a conversa e responda SOMENTE com JSON.
            
            CONVERSA:
            Usuário: %s
            GHOST: %s

            JSON ESPERADO:
            {"shouldSave": true, "content": "resumo do fato", "category": "personal", "importance": 8}
            OU
            {"shouldSave": false}
            """.formatted(userMessage, aiResponse);

        try {
            // Groq é rápido e barato -> ideal para essa tarefa de background
            String jsonDecision = callModelWithFallback(groqChatModel, geminiChatModel, evaluationPrompt, firebaseUid);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonDecision);

            if (node.path("shouldSave").asBoolean(false)) {
                String content = node.path("content").asText();
                String category = node.path("category").asText("other");
                int importance = node.path("importance").asInt(5);

                if (content != null && !content.isEmpty()) {
                    memoryService.saveMemory(content, firebaseUid, category, importance);
                    log.info("Memória salva: {}", content);
                }
            }
        } catch (Exception e) {
            log.error("Erro no auto-aprendizado (uid {}): {}", firebaseUid, e.getMessage());
        }
    }

    private String callModelWithFallback(ChatModel primary, ChatModel fallback, String promptText, String uid) {
        try {
            return primary.call(new Prompt(promptText)).getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("Modelo primário falhou no learning, tentando fallback...");
            return fallback.call(new Prompt(promptText)).getResult().getOutput().getText();
        }
    }
}