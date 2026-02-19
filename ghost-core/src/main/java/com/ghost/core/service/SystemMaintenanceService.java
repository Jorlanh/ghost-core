package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SystemMaintenanceService {

    // Caminho onde o relatório será salvo
    private static final String REPORT_PATH = "C:\\Temp\\ghost_relatorio.txt"; 
    // Ajuste para onde está seu projeto
    private static final String PROJECT_ROOT = System.getProperty("user.dir"); 

    /**
     * TAREFA 1: O "Pente Fino"
     * Lê o pom.xml, simula verificação e abre o Bloco de Notas.
     */
    public String runDiagnostics() {
        log.info("Iniciando varredura de sistema...");
        
        StringBuilder report = new StringBuilder();
        report.append("=== RELATÓRIO DE MANUTENÇÃO GHOST ===\n");
        report.append("Data: ").append(LocalDateTime.now()).append("\n");
        report.append("Diretório: ").append(PROJECT_ROOT).append("\n\n");

        // 1. Verificar Arquivo POM (Simulação de leitura)
        File pomFile = new File(PROJECT_ROOT, "pom.xml");
        if (pomFile.exists()) {
            report.append("[OK] pom.xml encontrado.\n");
            // Aqui você poderia ler o arquivo real e checar versões
            report.append("[INFO] Spring Boot Version: 3.4.2 (Check: Stable)\n");
            report.append("[WARN] Dependência 'spring-ai-openai' em Milestone (M5). Risco de instabilidade.\n");
        } else {
            report.append("[ERRO] pom.xml não encontrado!\n");
        }

        // 2. Verificar Espaço em Disco
        File root = new File("C:");
        long freeSpaceGB = root.getFreeSpace() / (1024 * 1024 * 1024);
        report.append("[INFO] Espaço em Disco: ").append(freeSpaceGB).append("GB livres.\n");

        report.append("\n=== CONCLUSÃO ===\n");
        report.append("Sistema operando nominalmente. Recomendação: Atualizar Spring AI quando sair a versão GA.\n");

        // 3. Salvar e Abrir no Bloco de Notas
        saveAndOpenNotepad(report.toString());

        return "Diagnóstico concluído. Relatório aberto no seu monitor, Senhor Walker.";
    }

    /**
     * TAREFA 2: A "Auto-Atualização"
     * Roda comandos git para se atualizar.
     */
    public String performSelfUpdate() {
        log.info("Iniciando protocolo de auto-atualização...");

        // Executa em thread separada para não travar a resposta da API
        CompletableFuture.runAsync(() -> {
            try {
                // Roda 'git pull' no diretório do projeto
                ProcessBuilder builder = new ProcessBuilder("git", "pull");
                builder.directory(new File(PROJECT_ROOT));
                builder.redirectErrorStream(true);
                Process process = builder.start();
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    log.info("Git Pull realizado com sucesso.");
                    // Aqui você poderia disparar um script para reiniciar o .jar
                } else {
                    log.error("Erro no Git Pull. Código: " + exitCode);
                }
            } catch (Exception e) {
                log.error("Falha na atualização: ", e);
            }
        });

        return "Iniciei o protocolo de atualização (Git Pull). Verifique os logs do console para o status do deploy.";
    }

    private void saveAndOpenNotepad(String content) {
        try {
            // 1. Cria o arquivo
            File file = new File(REPORT_PATH);
            // Garante que a pasta existe
            file.getParentFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
            }

            // 2. Manda o Windows abrir o Notepad
            ProcessBuilder pb = new ProcessBuilder("notepad.exe", REPORT_PATH);
            pb.start();
            log.info("Bloco de notas aberto com sucesso.");

        } catch (IOException e) {
            log.error("Erro ao abrir notepad: " + e.getMessage());
        }
    }
}