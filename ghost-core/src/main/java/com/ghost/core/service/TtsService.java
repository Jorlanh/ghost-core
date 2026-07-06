package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

@Service
@Slf4j
public class TtsService {

    private final RestClient restClient;
    private final String voiceBaseUrl;
    private final Path audioCacheDir;

    public TtsService(
            RestClient.Builder restClientBuilder,
            @Value("${ghost.voice.base-url:http://localhost:5001}") String voiceBaseUrl,
            @Value("${ghost.voice.cache-dir:audio_cache}") String audioCacheDir) {
        this.restClient = restClientBuilder.build();
        this.voiceBaseUrl = voiceBaseUrl;
        this.audioCacheDir = Paths.get(audioCacheDir);
        new File(audioCacheDir).mkdirs();
    }

    public String synthesize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        try {
            String sanitizedText = sanitizeText(text);
            String filename = generateHash(sanitizedText) + ".wav";
            Path outputPath = audioCacheDir.resolve(filename);

            if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                return "/audio_cache/" + filename;
            }

            String url = UriComponentsBuilder.fromHttpUrl(voiceBaseUrl)
                    .path("/speak")
                    .queryParam("text", sanitizedText)
                    .toUriString();

            byte[] wav = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);

            if (wav == null || wav.length == 0) {
                log.warn("ghost-voice returned empty audio.");
                return "";
            }

            Files.createDirectories(audioCacheDir);
            Files.write(outputPath, wav);
            return "/audio_cache/" + filename;
        } catch (Exception e) {
            log.warn("ghost-voice unavailable. Browser TTS fallback will be used. Cause: {}", e.getMessage());
            return "";
        }
    }

    private String sanitizeText(String text) {
        return text.replace("\"", "")
                .replace("'", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
