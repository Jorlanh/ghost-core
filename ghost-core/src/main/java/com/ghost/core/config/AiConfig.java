package com.ghost.core.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

import com.ghost.core.service.ApiConfigService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AiConfig {

    private final ApiConfigService apiConfigService;

    @Value("${groq.api-key:}")
    private String groqFallbackKey;

    // @Lazy aqui previne o ciclo: AiConfig -> ApiConfigService -> ... -> AiConfig
    public AiConfig(@Lazy ApiConfigService apiConfigService) {
        this.apiConfigService = apiConfigService;
    }

    // --- GROQ (MANUAL - Pois usa Starter OpenAI genérico) ---
    @Bean
    @Qualifier("groqChatModel")
    public OpenAiChatModel groqChatModel(RestClient.Builder restClientBuilder) {
        String apiKey = null;

        try {
            apiKey = apiConfigService.getApiKey("GROQ_LLAMA");
            log.debug("Chave GROQ carregada do banco/cache.");
        } catch (Exception e) {
            log.warn("Falha ao obter chave GROQ (normal no boot se lazy): {}", e.getMessage());
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = groqFallbackKey;
            if (apiKey == null || apiKey.trim().isEmpty()) apiKey = System.getenv("GROQ_API_KEY");
            if (apiKey == null || apiKey.trim().isEmpty()) apiKey = "gsk_dummy_key_startup_only";
        }

        OpenAiApi groqApi = OpenAiApi.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("llama-3.3-70b-versatile")
                .temperature(0.6)
                .maxTokens(8192)
                .build();

        log.info("Infra: Groq ChatModel Inicializado.");
        return OpenAiChatModel.builder()
                .openAiApi(groqApi)
                .defaultOptions(options)
                .build();
    }

    // --- ChatClients (Fluentes) ---
    
    @Bean
    public ChatClient groqChatClient(@Qualifier("groqChatModel") ChatModel groqModel) {
        return ChatClient.builder(groqModel)
                .defaultSystem("Você é o motor de processamento ultra-rápido do GHOST. Priorize velocidade e precisão técnica.")
                .build();
    }

    // CORREÇÃO: Usa o nome oficial do bean do Spring AI Google Starter
    @Bean
    public ChatClient geminiChatClient(@Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        log.info("Infra: Criando ChatClient para Gemini.");
        return ChatClient.builder(geminiModel)
                .defaultSystem("Você é o núcleo de inteligência avançada do GHOST. Responda de forma técnica e precisa.")
                .build();
    }
}