package com.ghost.core.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LocalCyberOpsService {

    /**
     * Levanta um contentor Kali Linux efêmero, roda o Nmap contra um alvo local e destrói o contentor.
     */
    public String runLocalPortScan(String targetIp) {
        log.info("[CYBER-OPS] Iniciando varredura Kali Linux no alvo: {}", targetIp);
        
        // Comando: docker run --rm kalilinux/kali-rolling nmap -sV [ALVO]
        // O --rm garante que o contentor é apagado logo após o uso.
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "--rm", "kalilinux/kali-rolling", "nmap", "-sV", "-p-", targetIp
        );

        try {
            Process process = processBuilder.start();
            
            // Lendo a saída do terminal do Kali Linux
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));
            
            process.waitFor();
            log.info("[CYBER-OPS] Varredura concluída.");
            
            return output;
            
        } catch (Exception e) {
            log.error("[CYBER-OPS] Falha ao executar protocolo de varredura.", e);
            return "Erro ao iniciar o contentor Kali: " + e.getMessage();
        }
    }
}