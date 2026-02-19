package com.ghost.core.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {

    private final ChatModel geminiModel;
    private final ChatModel groqModel;

    // Alinhado com AiConfig: googleGenAiChatModel (auto-config Gemini) e groqChatModel (seu bean custom)
    public OrchestrationService(
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel,
            @Qualifier("groqChatModel") ChatModel groqModel) {
        this.geminiModel = geminiModel;
        this.groqModel = groqModel;
    }

    /**
     * Pergunta ao Gemini usando a API baixa-nível (ChatModel + Prompt).
     * Útil quando precisar de controle fino sobre Prompt ou opções.
     */
    public String askGeminiRaw(String promptText) {
        Prompt prompt = new Prompt(promptText);
        ChatResponse response = geminiModel.call(prompt);
        return response.getResult().getOutput().getText();  // getText() é o método correto
    }

    /**
     * Pergunta ao Groq usando a API baixa-nível.
     */
    public String askGroqRaw(String promptText) {
        Prompt prompt = new Prompt(promptText);
        ChatResponse response = groqModel.call(prompt);
        return response.getResult().getOutput().getText();  // getText() é o método correto
    }

    /**
     * Método recomendado: Usa ChatClient (API fluente e moderna).
     * Mais simples, abstrai Prompt/ChatResponse, suporta .system(), .tools(), streaming, etc.
     */
    public String askWithClient(ChatModel model, String promptText) {
        return ChatClient.create(model)
                .prompt()
                .user(promptText)
                .call()
                .content();  // Retorna String diretamente (melhor prática)
    }

    // Métodos de conveniência usando ChatClient (recomendado para uso diário)
    public String askGemini(String promptText) {
        return askWithClient(geminiModel, promptText);
    }

    public String askGroq(String promptText) {
        return askWithClient(groqModel, promptText);
    }

    /**
     * Roteamento inteligente simples (ex: prefere Groq para velocidade).
     * Pode expandir com lógica de latência, custo, comprimento do prompt, etc.
     */
    public String orchestrate(String promptText, boolean preferSpeed) {
        ChatModel selected = preferSpeed ? groqModel : geminiModel;
        return askWithClient(selected, promptText);
    }
}