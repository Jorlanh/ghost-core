package com.ghost.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioTranscriptionService {

    private final ApiConfigService apiConfigService;
    private final RestTemplate restTemplate = new RestTemplate();

    public String transcribe(MultipartFile audioFile) {
        try {
            String groqApiKey = apiConfigService.getApiKey("GROQ_LLAMA");
            if (groqApiKey == null || groqApiKey.isEmpty()) {
                log.error("GHOST >> Chave da API do Groq não encontrada para transcrição.");
                throw new RuntimeException("API Key Ausente");
            }

            String url = "https://api.groq.com/openai/v1/audio/transcriptions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(groqApiKey);

            ByteArrayResource audioResource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return "command.webm";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", audioResource);
            body.add("model", "whisper-large-v3");
            body.add("language", "pt");
            body.add("response_format", "json");
            
            // O SEGREDO DA PRECISÃO
            String vocabularyContext = "Senhor Walker, GHOST, VS Code, Spotify, playlist, WhatsApp, mensagem, ligar, contato, React, Java, Spring Boot, PowerShell, script, CMD, código, atualizar, reproduzir.";
            body.add("prompt", vocabularyContext);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("GHOST >> Disparando requisição neural Whisper para Groq...");
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("text")) {
                return (String) response.getBody().get("text");
            }

            return "";

        } catch (Exception e) {
            log.error("GHOST >> Falha na transcrição neural (Whisper): {}", e.getMessage());
            throw new RuntimeException("Erro ao processar áudio", e);
        }
    }
}