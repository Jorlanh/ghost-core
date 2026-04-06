package com.ghost.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgenticService {

    private final DeviceControlService deviceService;
    private final IntelligenceService intelligenceService;

    // =========================================================================
    // NÍVEL 8: SENTINELA SOB DEMANDA (Leitura do último arquivo baixado)
    // =========================================================================
    public String analyzeLastDownload(String nickname, String uid) {
        try {
            String userHome = System.getProperty("user.home");
            File downloadsFolder = new File(userHome, "Downloads");

            if (!downloadsFolder.exists() || !downloadsFolder.isDirectory()) {
                return "Senhor, não consegui localizar o diretório de downloads.";
            }

            File[] files = downloadsFolder.listFiles();
            if (files == null || files.length == 0) {
                return "Sua pasta de downloads está vazia, Senhor Walker.";
            }

            // Pega o arquivo mais recente
            File lastModifiedFile = Arrays.stream(files)
                    .filter(File::isFile)
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);

            if (lastModifiedFile == null) return "Nenhum arquivo válido encontrado.";

            String fileName = lastModifiedFile.getName();
            
            // Lógica de leitura de arquivos de texto (códigos, txt, json, csv)
            if (fileName.endsWith(".txt") || fileName.endsWith(".json") || fileName.endsWith(".csv") || fileName.endsWith(".js") || fileName.endsWith(".java") || fileName.endsWith(".md")) {
                String content = Files.readString(lastModifiedFile.toPath());
                // Evita estourar os tokens cortando arquivos gigantes
                if (content.length() > 15000) content = content.substring(0, 15000) + "... [CONTEÚDO TRUNCADO]";
                
                String prompt = "O usuário pediu para você analisar o último arquivo baixado. O nome do arquivo é " + fileName + ". O conteúdo é:\n\n" + content + "\n\nFaça um resumo executivo ou encontre erros/armadilhas se for um código/contrato.";
                return intelligenceService.getAiResponse(prompt, nickname, false, uid);
            } 
            else {
                return "O último arquivo baixado é '" + fileName + "'. Atualmente, meu córtex de leitura lê apenas formatos de texto ou código. Para PDFs, precisarei de uma atualização de OCR, Senhor.";
            }

        } catch (Exception e) {
            log.error("Erro na Sentinela Sob Demanda: {}", e.getMessage());
            return "Ocorreu um erro ao tentar acessar os seus arquivos recentes.";
        }
    }

    // =========================================================================
    // NÍVEL 9 E 10: LOOP DE AGENTE (Agentic Workflow & Self-Expansion)
    // =========================================================================
    public String executeAgenticLoop(String objective, String nickname, String uid) {
        log.info("GHOST >> Iniciando Loop Autônomo (Nível 9). Objetivo: {}", objective);
        
        String currentContext = "OBJETIVO DO AGENTE: " + objective + "\nVocê deve criar arquivos, rodar comandos no PowerShell para testar, ler os erros e consertar o código sozinho até o objetivo ser cumprido.\n";
        String loopConclusion = "Objetivo concluído com sucesso.";
        
        // Limite de 3 iterações para ele não ficar em loop infinito gastando sua API
        int maxIterations = 3; 

        for (int i = 1; i <= maxIterations; i++) {
            log.info("GHOST >> Iteração Autônoma {}/{}", i, maxIterations);
            
            // Pergunta para a IA o que ela quer fazer agora
            String aiAction = intelligenceService.getAiResponse(currentContext + "\nO que você vai fazer agora? Responda APENAS com a tag <action> contendo type: POWERSHELL e o comando.", nickname, true, uid);
            
            if (!aiAction.contains("<action>")) {
                loopConclusion = aiAction; // A IA decidiu que terminou e respondeu em texto normal
                break;
            }

            try {
                // Extrai e executa o comando PowerShell que a IA gerou
                String jsonStr = aiAction.substring(aiAction.indexOf("<action>") + 8, aiAction.indexOf("</action>")).trim();
                String psCommand = jsonStr.split("\"command\":\\s*\"")[1].split("\"")[0]; // Parse simples rápido
                
                log.info("GHOST executando autonomamente: {}", psCommand);
                
                // Executa no Windows e pega a saída do terminal (o erro ou sucesso)
                String terminalOutput = deviceService.executeWindowsCommand("powershell.exe -Command \"" + psCommand.replace("\"", "\\\"") + "\"", true);
                
                // Alimenta o contexto com o resultado do erro para a próxima iteração do loop
                currentContext += "\nPasso " + i + " executado: " + psCommand + "\nResultado do Terminal: " + terminalOutput + "\nSe deu erro, conserte o comando. Se deu certo, prossiga ou encerre.";
                
                if (terminalOutput.contains("Erro") || terminalOutput.contains("Exception")) {
                    log.warn("GHOST encontrou um erro no próprio código. Corrigindo na próxima iteração...");
                } else if (i == maxIterations) {
                    loopConclusion = "Senhor Walker, atingi o limite de iterações autônomas. O último status foi: " + terminalOutput;
                }

            } catch (Exception e) {
                log.error("Erro no loop autônomo: {}", e.getMessage());
                break;
            }
        }
        
        return "Processo autônomo finalizado, Senhor. " + loopConclusion;
    }
}