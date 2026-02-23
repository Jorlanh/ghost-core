package com.ghost.core.controller;

import com.ghost.core.service.DeviceControlService;
import com.ghost.core.service.IntelligenceService;
import com.ghost.core.service.SystemMaintenanceService;
import com.ghost.core.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ghost")
@RequiredArgsConstructor
@Slf4j
// @CrossOrigin(origins = "*")
public class GhostController {

    private final IntelligenceService intelligenceService;
    private final SystemMaintenanceService maintenanceService;
    private final DeviceControlService deviceService;
    private final TtsService ttsService;

    // Novo payload recebe o clientSource para Roteamento Híbrido
    public record InteractionRequest(String command, String uid, String clientSource) {}

    // Objeto interno para devolver texto falado e comando de máquina simultaneamente
    private record CommandResult(String text, String osCommand) {}

    @PostMapping("/interact")
    public ResponseEntity<Map<String, Object>> interact(@RequestBody InteractionRequest request) {
        String rawCommand = request.command() != null ? request.command().trim() : "";
        String lowerCommand = rawCommand.toLowerCase();

        String nickname = "Visitante";
        boolean isGodMode = request.uid() != null && !request.uid().isBlank();

        if (isGodMode) {
            String uidClean = request.uid().replaceAll("[^a-zA-Z0-9]", " ").trim();
            nickname = "Senhor " + (uidClean.isEmpty() ? "Usuário" : capitalizeFirst(uidClean));
        }

        // Processa o comando com a matriz de decisão
        CommandResult result = processCommand(lowerCommand, rawCommand, nickname, isGodMode, request);

        // Gera áudio neural (em bloco try/catch para não quebrar se a API falhar)
        String audioUrl = "";
        try {
            audioUrl = ttsService.synthesize(result.text());
        } catch (Exception e) {
            log.error("TTS falhou. Fallback para síntese nativa do browser.");
        }

        return ResponseEntity.ok(Map.of(
            "response", result.text(),
            "user", nickname,
            "audioUrl", audioUrl != null ? audioUrl : "",
            "osCommand", result.osCommand(), // O Electron reage a esta variável!
            "status", "SUCCESS"
        ));
    }

    private CommandResult processCommand(String lowerCommand, String rawCommand, String nickname, boolean isGodMode, InteractionRequest request) {
        if (!isGodMode) {
            return new CommandResult(intelligenceService.getAiResponse(rawCommand, nickname, false, request.uid()), "");
        }

        String[] confirmPhrases = {
            "É pra já, " + nickname + ".",
            "Deixa comigo, " + nickname + ".",
            "Imediatamente, meu senhor.",
            "Já estou executando, " + nickname + "."
        };
        String confirmation = confirmPhrases[(int)(Math.random() * confirmPhrases.length)];

        // Identifica quem enviou o comando (ELECTRON, WEB, MOBILE)
        String client = request.clientSource() != null ? request.clientSource().toUpperCase() : "WEB";
        
        String actionResult = "";
        String conclusion = "Pronto, " + nickname + ". Algo mais?";
        String osAction = ""; // O comando que será enviado ao Electron

        // ---------------------------------------------------------
        // MATRIZ DE DECISÃO HÍBRIDA (PC / SISTEMA OPERACIONAL)
        // ---------------------------------------------------------
        if (lowerCommand.startsWith("execute no pc") || lowerCommand.startsWith("rode no pc") || lowerCommand.startsWith("faça no pc")) {
            String prefix = getPrefix(lowerCommand, new String[]{"execute no pc", "rode no pc", "faça no pc"});
            String subCmd = rawCommand.substring(prefix.length()).trim();
            boolean ps = subCmd.toLowerCase().contains("powershell") || subCmd.toLowerCase().contains("ps1");

            if (client.equals("ELECTRON")) {
                osAction = ps ? "powershell -Command \"" + subCmd + "\"" : subCmd;
                conclusion = "Delegação local aprovada. Executando via Electron, " + nickname + ".";
            } else {
                actionResult = deviceService.executeWindowsCommand(subCmd, ps);
                conclusion = "Comando executado remotamente pelo servidor. Status: " + actionResult;
            }
        }
        else if (lowerCommand.contains("desligar pc") || lowerCommand.contains("desligar o computador")) {
            if (client.equals("ELECTRON")) {
                osAction = "shutdown /s /t 5";
                conclusion = "Protocolo de desligamento local ativado. Até breve, " + nickname + ".";
            } else {
                deviceService.executeWindowsCommand("shutdown /s /t 5", false);
                conclusion = "Desligando o servidor central remotamente. Até logo.";
            }
        }
        else if (lowerCommand.contains("reiniciar pc") || lowerCommand.contains("reiniciar o computador")) {
            if (client.equals("ELECTRON")) {
                osAction = "shutdown /r /t 5";
                conclusion = "Reinício local agendado, " + nickname + ".";
            } else {
                deviceService.executeWindowsCommand("shutdown /r /t 5", false);
                conclusion = "Reiniciando o núcleo do servidor remotamente.";
            }
        }
        else if (lowerCommand.contains("aumentar volume") || lowerCommand.contains("volume up") || lowerCommand.contains("sobe o som")) {
            if (client.equals("ELECTRON")) {
                // Roteamento para fallback interno no Electron ou NirCmd
                osAction = "VOLUME_UP"; 
                conclusion = "Volume do terminal ajustado para cima.";
            } else {
                deviceService.executeWindowsCommand("nircmd.exe changesysvolume 10000", false);
                conclusion = "Volume do servidor remoto aumentado.";
            }
        }
        else if (lowerCommand.contains("diminuir volume") || lowerCommand.contains("volume down") || lowerCommand.contains("baixa o som")) {
            if (client.equals("ELECTRON")) {
                osAction = "VOLUME_DOWN";
                conclusion = "Volume do terminal reduzido, " + nickname + ".";
            } else {
                deviceService.executeWindowsCommand("nircmd.exe changesysvolume -10000", false);
                conclusion = "Volume do servidor remoto diminuído.";
            }
        }
        else if (lowerCommand.contains("mudo") || lowerCommand.contains("silenciar") || lowerCommand.contains("fica quieto")) {
            if (client.equals("ELECTRON")) {
                osAction = "VOLUME_MUTE";
                conclusion = "Sistema em modo silencioso.";
            } else {
                deviceService.executeWindowsCommand("nircmd.exe mutesysvolume 2", false);
                conclusion = "Servidor remoto silenciado.";
            }
        }
        else if (lowerCommand.contains("bloquear tela") || lowerCommand.contains("lock screen")) {
            if (client.equals("ELECTRON")) {
                osAction = "rundll32.exe user32.dll,LockWorkStation";
                conclusion = "Acesso ao terminal bloqueado. Sistema seguro.";
            } else {
                deviceService.executeWindowsCommand("rundll32.exe user32.dll,LockWorkStation", false);
                conclusion = "Servidor remoto travado com sucesso.";
            }
        }

        // ---------------------------------------------------------
        // COMANDOS DIRETOS DO SERVIDOR (Mobile / Manutenção / ADB)
        // ---------------------------------------------------------
        else if (lowerCommand.startsWith("execute no android") || lowerCommand.startsWith("rode no android")) {
            String prefix = getPrefix(lowerCommand, new String[]{"execute no android", "rode no android"});
            String subCmd = rawCommand.substring(prefix.length()).trim();
            actionResult = deviceService.executeAdbCommand(subCmd);
            conclusion = actionResult.contains("OK") ? "Link neural com o Android confirmado." : "Falha na ponte ADB.";
        }
        else if (lowerCommand.contains("tirar print") || lowerCommand.contains("screenshot no celular")) {
            deviceService.executeAdbCommand("shell screencap -p /sdcard/ghost-screenshot.png");
            conclusion = "Screenshot salvo na memória do dispositivo móvel, " + nickname + ".";
        }
        else if (lowerCommand.contains("pente fino") || lowerCommand.contains("diagnostico")) {
            actionResult = maintenanceService.runDiagnostics();
            conclusion = "Diagnóstico completo finalizado.";
        }
        else {
            // Se não for comando de hardware, a IA processa o diálogo normalmente
            String aiResponse = intelligenceService.getAiResponse(rawCommand, nickname, true, request.uid());
            return new CommandResult(confirmation + "\n" + aiResponse, "");
        }

        return new CommandResult(confirmation + "\n" + conclusion, osAction);
    }

    private String getPrefix(String lowerCommand, String[] possiblePrefixes) {
        for (String prefix : possiblePrefixes) {
            if (lowerCommand.startsWith(prefix)) return prefix;
        }
        return "";
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}