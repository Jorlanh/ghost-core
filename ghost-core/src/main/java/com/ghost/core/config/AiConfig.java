package com.ghost.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

    @Bean
    public RestClient ollamaRestClient(
            RestClient.Builder builder,
            @Value("${ghost.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        return builder.baseUrl(ollamaBaseUrl).build();
    }
}
