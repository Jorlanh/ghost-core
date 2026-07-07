package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SystemMaintenanceService {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");

    public String runDiagnostics() {
        Runtime runtime = Runtime.getRuntime();
        File root = new File(System.getProperty("user.dir")).getAbsoluteFile();
        File drive = root.toPath().getRoot() != null ? root.toPath().getRoot().toFile() : new File("C:");

        StringBuilder report = new StringBuilder();
        report.append("=== GHOST DIAGNOSTICS ===\n");
        report.append("Timestamp: ").append(LocalDateTime.now()).append("\n");
        report.append("Project root: ").append(PROJECT_ROOT).append("\n");
        report.append("Java: ").append(System.getProperty("java.version")).append("\n");
        report.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        report.append("Uptime(ms): ").append(ManagementFactory.getRuntimeMXBean().getUptime()).append("\n");
        report.append("CPU cores: ").append(runtime.availableProcessors()).append("\n");
        report.append("Memory free/total/max MB: ")
                .append(runtime.freeMemory() / 1024 / 1024).append("/")
                .append(runtime.totalMemory() / 1024 / 1024).append("/")
                .append(runtime.maxMemory() / 1024 / 1024).append("\n");
        report.append("Disk free GB: ").append(drive.getFreeSpace() / 1024 / 1024 / 1024).append("\n");
        report.append("Ollama URL: ").append(env("OLLAMA_BASE_URL", "http://localhost:11434")).append("\n");
        report.append("Voice URL: ").append(env("GHOST_VOICE_URL", "http://localhost:5001")).append("\n");
        report.append("STT command configured: ").append(!env("GHOST_STT_COMMAND", "").isBlank()).append("\n");
        report.append("Allowed scan targets: ").append(env("GHOST_ALLOWED_SCAN_TARGETS", "private-networks-only")).append("\n");
        report.append("Status: NOMINAL\n");
        return report.toString();
    }

    public Map<String, Object> runDiagnosticsJson() {
        Runtime runtime = Runtime.getRuntime();
        return Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "projectRoot", PROJECT_ROOT,
                "java", System.getProperty("java.version"),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime(),
                "cpuCores", runtime.availableProcessors(),
                "freeMemoryMb", runtime.freeMemory() / 1024 / 1024,
                "totalMemoryMb", runtime.totalMemory() / 1024 / 1024,
                "maxMemoryMb", runtime.maxMemory() / 1024 / 1024,
                "ollamaUrl", env("OLLAMA_BASE_URL", "http://localhost:11434"),
                "voiceUrl", env("GHOST_VOICE_URL", "http://localhost:5001")
        );
    }

    public String performSelfUpdate() {
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder("git", "status", "--short");
                builder.directory(new File(PROJECT_ROOT));
                builder.redirectErrorStream(true);
                int exitCode = builder.start().waitFor();
                log.info("Self-check git status completed with exit {}", exitCode);
            } catch (Exception e) {
                log.warn("Self-check unavailable: {}", e.getMessage());
            }
        });

        return "Autoanalise iniciada. Mudancas reais continuam exigindo autorizacao do operador antes do merge.";
    }

    private String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
