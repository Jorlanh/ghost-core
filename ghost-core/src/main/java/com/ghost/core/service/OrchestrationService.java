package com.ghost.core.service;

import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {

    private final OllamaClientService ollamaClientService;

    public OrchestrationService(OllamaClientService ollamaClientService) {
        this.ollamaClientService = ollamaClientService;
    }

    public String askLocal(String promptText) {
        String system = "Voce e o nucleo tecnico local do GHOST. Responda em portugues, com precisao e objetividade.";
        String response = ollamaClientService.chatWithFallback(system, promptText);
        return response == null || response.isBlank()
                ? "Ollama local indisponivel. Inicie o servico e baixe llama3:8b ou deepseek-r1:8b."
                : response;
    }

    public String askGeminiRaw(String promptText) {
        return askLocal(promptText);
    }

    public String askGroqRaw(String promptText) {
        return askLocal(promptText);
    }

    public String askWithClient(Object ignoredModel, String promptText) {
        return askLocal(promptText);
    }

    public String askGemini(String promptText) {
        return askLocal(promptText);
    }

    public String askGroq(String promptText) {
        return askLocal(promptText);
    }

    public String orchestrate(String promptText, boolean preferSpeed) {
        return askLocal(promptText);
    }
}
