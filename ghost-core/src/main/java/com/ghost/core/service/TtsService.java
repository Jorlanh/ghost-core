package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TtsService {

    // Caminhos (Ajuste conforme sua estrutura real)
    private static final String AUDIO_CACHE_DIR = "target/classes/static/audio_cache";
    private static final String PIPER_BIN = "bin/piper/piper.exe"; 
    private static final String PIPER_MODEL = "bin/piper/pt_br-faber-medium.onnx";
    
    // Voz do Edge (Antonio é a melhor masculina PT-BR)
    private static final String EDGE_VOICE = "pt-BR-AntonioNeural"; 

    public TtsService() {
        // Garante que a pasta de cache existe ao iniciar
        new File(AUDIO_CACHE_DIR).mkdirs();
    }

    /**
     * Gera o áudio usando Estratégia Híbrida (Online -> Fallback Offline)
     * Retorna o caminho relativo do arquivo para ser servido pelo Spring Web.
     */
    public String synthesize(String text) {
        try {
            String sanitizedText = sanitizeText(text);
            String filename = generateHash(sanitizedText) + ".mp3";
            Path outputPath = Paths.get(AUDIO_CACHE_DIR, filename);

            // 1. Verificar Cache (Velocidade da Luz)
            if (Files.exists(outputPath)) {
                log.info("Audio Cache HIT: {}", filename);
                return "/audio_cache/" + filename;
            }

            // 2. Tentar Edge-TTS (Qualidade Máxima)
            boolean success = generateWithEdge(sanitizedText, outputPath.toString());

            // 3. Fallback para Piper (Modo Bunker) se Edge falhar
            if (!success) {
                log.warn("Edge-TTS falhou ou offline. Ativando Protocolo PIPER.");
                filename = filename.replace(".mp3", ".wav"); // Piper gera wav
                outputPath = Paths.get(AUDIO_CACHE_DIR, filename);
                
                if (Files.exists(outputPath)) { // Checa cache do wav também
                     return "/audio_cache/" + filename;
                }
                
                generateWithPiper(sanitizedText, outputPath.toString());
            }

            // Retorna o caminho web acessível
            return "/audio_cache/" + filename;

        } catch (Exception e) {
            log.error("Erro crítico no TTS: ", e);
            return null; // Frontend tratará silêncio
        }
    }

    private boolean generateWithEdge(String text, String outputPath) {
        try {
            // Comando: edge-tts --text "Texto" --write-media "arquivo.mp3" --voice pt-BR-AntonioNeural
            ProcessBuilder pb = new ProcessBuilder(
                "edge-tts",
                "--text", text,
                "--write-media", outputPath,
                "--voice", EDGE_VOICE
            );
            
            Process p = pb.start();
            boolean finished = p.waitFor(10, TimeUnit.SECONDS); // Timeout de 10s para não travar
            
            return finished && p.exitValue() == 0 && new File(outputPath).length() > 0;
        } catch (Exception e) {
            log.error("Falha ao executar Edge-TTS: {}", e.getMessage());
            return false;
        }
    }

    private boolean generateWithPiper(String text, String outputPath) {
        try {
            // Piper recebe texto via STDIN (echo "texto" | piper ...)
            // No Java, escrevemos no outputStream do processo
            ProcessBuilder pb = new ProcessBuilder(
                PIPER_BIN,
                "--model", PIPER_MODEL,
                "--output_file", outputPath
            );
            
            Process p = pb.start();
            
            // Escreve o texto no input do Piper
            try (var os = p.getOutputStream()) {
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            log.error("Falha crítica no Piper: {}", e.getMessage());
            return false;
        }
    }

    private String sanitizeText(String text) {
        // Remove caracteres que podem quebrar o comando shell
        return text.replace("\"", "").replace("'", "").replace("\n", " ");
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}