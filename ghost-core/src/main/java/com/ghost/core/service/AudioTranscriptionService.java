package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AudioTranscriptionService {

    private final String localSttCommand;

    public AudioTranscriptionService(@Value("${ghost.stt.command:}") String localSttCommand) {
        this.localSttCommand = localSttCommand;
    }

    public String transcribe(MultipartFile audioFile) {
        if (localSttCommand == null || localSttCommand.isBlank()) {
            throw new IllegalStateException(
                    "STT local nao configurado. Use o frontend com Web Speech ou defina GHOST_STT_COMMAND.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ghost-command-", ".webm");
            audioFile.transferTo(tempFile);

            String command = localSttCommand.replace("{file}", tempFile.toAbsolutePath().toString());
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n")).trim();
            }

            if (!finished || process.exitValue() != 0) {
                throw new IllegalStateException("Falha no STT local: " + output);
            }

            return output;
        } catch (Exception e) {
            log.warn("Falha na transcricao local: {}", e.getMessage());
            throw new RuntimeException("Erro ao processar audio localmente", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
