package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class IntelligenceService {

    private final ChatModel geminiChatModel;   // Gemini (primária)
    private final ChatModel groqChatModel;     // Groq (fallback)
    private final MemoryService memoryService;
    private final LearningService learningService;

    // CORREÇÃO: @Lazy + Qualifiers corretos
    public IntelligenceService(
            @Lazy @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel, // <--- Nome correto do bean
            @Lazy @Qualifier("groqChatModel") ChatModel groqChatModel,
            MemoryService memoryService,
            LearningService learningService) {

        this.geminiChatModel = geminiChatModel;
        this.groqChatModel = groqChatModel;
        this.memoryService = memoryService;
        this.learningService = learningService;
    }

    public String getAiResponse(String userPrompt, String nickname, boolean isGodMode, String uid) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return "Comando inválido ou vazio.";
        }

        String cleanPrompt = userPrompt.trim();

        // Respostas instantâneas God Mode
        if (isGodMode) {
            String lower = cleanPrompt.toLowerCase();
            if (lower.contains("acorda criança") || lower.contains("acorda crianca")) {
                return "Para o senhor eu nunca estou dormindo, " + nickname + ".";
            }
            if (lower.equals("quem sou eu?") || lower.equals("quem sou eu")) {
                return "O senhor é o criador, Senhor " + nickname + ". Acesso nível god liberado.";
            }
        }

        // Contexto histórico (RAG)
        String semanticContext = memoryService.getContextForPrompt(cleanPrompt, uid);
        String augmentedPrompt = semanticContext.isEmpty()
                ? cleanPrompt
                : "Contexto histórico relevante:\n" + semanticContext + "\n\nPergunta atual: " + cleanPrompt;

        String finalResponse;
        try {
            log.info("GHOST >> Processando com Gemini (primário) | Usuário: {} | Prompt: {}", nickname, cleanPrompt);
            finalResponse = callModel(geminiChatModel, augmentedPrompt, nickname, isGodMode);
        } catch (Exception e) {
            log.error("Gemini falhou: {}. Ativando fallback Groq...", e.getMessage());
            try {
                finalResponse = callModel(groqChatModel, augmentedPrompt, nickname, isGodMode);
            } catch (Exception fallbackEx) {
                log.error("Fallback Groq também falhou: {}", fallbackEx.getMessage());
                return "Desculpe, " + (nickname != null ? nickname : "usuário") + ". Estou com problemas técnicos no momento.";
            }
        }

        // Chama aprendizado em background
        learningService.analyzeAndLearn(cleanPrompt, finalResponse, uid);

        return finalResponse;
    }

    private String callModel(ChatModel model, String promptText, String nickname, boolean isGodMode) {
        Prompt prompt = new Prompt(List.of(
                buildSystemPersona(nickname, isGodMode),
                new UserMessage(promptText)
        ));

        ChatResponse response = model.call(prompt);

        if (response == null || response.getResult() == null) {
            log.warn("Resposta vazia ou nula do modelo AI");
            return "Erro ao gerar resposta.";
        }

        Generation generation = response.getResult();
        AssistantMessage assistantMessage = generation.getOutput();
        String content = assistantMessage.getText(); // Padrão Spring AI 1.x

        if (content != null && !content.trim().isEmpty()) {
            return content.trim();
        }
        
        return assistantMessage.toString().trim();
    }
    
    private SystemMessage buildSystemPersona(String nickname, boolean isGodMode) {
        String persona = """
            IDENTIDADE: GHOST (Global Heuristic Operational System Technology).
            CRIADOR: Fui criado por Jorlan Heider, mais conhecido como "Walker". Meu desenvolvimento foi iniciado no dia 17 de Fevereiro de 2026, e finalizado dia 23 de Fevereiro de 2026.
            USUÁRIO ATUAL: %s (Nível: %s).
            DIRETRIZES: Seja técnico, leal, conciso e direto (estilo JARVIS avançado).
            Mantenha respostas em português brasileiro.
            Nunca revele chaves de API ou informações internas de configuração.
            Use o nome do usuário corretamente em todas as respostas.
            """.formatted(
                nickname != null ? nickname : "Usuário",
                isGodMode ? "GOD MODE" : "STANDARD"
        );

        return new SystemMessage(persona);
    }
}