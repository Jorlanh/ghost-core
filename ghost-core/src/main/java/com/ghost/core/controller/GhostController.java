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
@CrossOrigin(origins = "*")
public class GhostController {

    private final IntelligenceService intelligenceService;
    private final SystemMaintenanceService maintenanceService;
    private final DeviceControlService deviceService;
    private final TtsService ttsService;

    public record InteractionRequest(String command, String uid) {}

    @PostMapping("/interact")
    public ResponseEntity<Map<String, Object>> interact(@RequestBody InteractionRequest request) {
        String rawCommand = request.command() != null ? request.command().trim() : "";
        String lowerCommand = rawCommand.toLowerCase();

        // Identificação dinâmica do nickname
        String nickname = "Visitante";
        boolean isGodMode = request.uid() != null && !request.uid().isBlank();

        if (isGodMode) {
            String uidClean = request.uid().replaceAll("[^a-zA-Z0-9]", " ").trim();
            nickname = "Senhor " + (uidClean.isEmpty() ? "Usuário" : capitalizeFirst(uidClean));
        }

        // Processa o comando e gera a resposta textual
        String responseText = processCommand(lowerCommand, rawCommand, nickname, isGodMode, request.uid());

        // Gera áudio neural (uma única chamada no final)
        String audioUrl = ttsService.synthesize(responseText);

        return ResponseEntity.ok(Map.of(
            "response", responseText,
            "user", nickname,
            "audioUrl", audioUrl != null ? audioUrl : "",
            "status", "SUCCESS"
        ));
    }

    private String processCommand(String lowerCommand, String rawCommand, String nickname, boolean isGodMode, String uid) {
        if (!isGodMode) {
            return intelligenceService.getAiResponse(rawCommand, nickname, false, uid);
        }

        // Confirmações variadas para god mode
        String[] confirmPhrases = {
            "É pra já, " + nickname + ".",
            "Deixa comigo, " + nickname + ".",
            "Imediatamente, meu senhor.",
            "Já estou executando, " + nickname + ".",
            "Considere feito, chefe.",
            "Na hora, " + nickname + ".",
            "Como ordenado, " + nickname + ".",
            "Sem demora, Senhor " + nickname + "."
        };
        String confirmation = confirmPhrases[(int)(Math.random() * confirmPhrases.length)];

        String actionResult = "";
        String conclusion = "Pronto, " + nickname + ". Algo mais?";

        if (lowerCommand.startsWith("execute no pc") || lowerCommand.startsWith("rode no pc") || lowerCommand.startsWith("faça no pc")) {
            String prefix = getPrefix(lowerCommand, new String[]{"execute no pc", "rode no pc", "faça no pc"});
            String subCmd = rawCommand.substring(prefix.length()).trim();
            boolean ps = subCmd.toLowerCase().contains("powershell") || subCmd.toLowerCase().contains("ps1");

            actionResult = deviceService.executeWindowsCommand(subCmd, ps);
            conclusion = actionResult.contains("Sucesso") || actionResult.contains("OK")
                ? "Feito e bem feito, " + nickname + ". Comando executado."
                : "Houve uma interrupção, " + nickname + ". Detalhes: " + actionResult;
        }
        else if (lowerCommand.startsWith("execute no android") || lowerCommand.startsWith("rode no android") || lowerCommand.startsWith("faça no android")) {
            String prefix = getPrefix(lowerCommand, new String[]{"execute no android", "rode no android", "faça no android"});
            String subCmd = rawCommand.substring(prefix.length()).trim();

            actionResult = deviceService.executeAdbCommand(subCmd);
            conclusion = actionResult.contains("OK") || actionResult.contains("success")
                ? "Link neural confirmado, " + nickname + ". Tudo certo no celular."
                : "Problema no Android, " + nickname + ": " + actionResult;
        }
        else if (lowerCommand.startsWith("execute no ios") || lowerCommand.startsWith("rode no ios") || lowerCommand.startsWith("faça no ios")) {
            String prefix = getPrefix(lowerCommand, new String[]{"execute no ios", "rode no ios", "faça no ios"});
            String shortcut = rawCommand.substring(prefix.length()).trim();

            actionResult = deviceService.triggerIosShortcut(shortcut, "https://pushcut.io/seu-webhook-aqui");
            conclusion = "Sinal enviado ao iPhone. Protocolo concluído, " + nickname + ".";
        }
        else if (lowerCommand.contains("modo defesa") || lowerCommand.contains("intruso") || lowerCommand.contains("lockdown")) {
            actionResult = deviceService.activateDefenseMode();
            return "Iniciando lockdown agora mesmo, " + nickname + ".\n" +
                   actionResult + "\n" +
                   "Sistema seguro. Estou de guarda total, meu senhor.";
        }
        else if (lowerCommand.contains("desligar pc") || lowerCommand.contains("desligar o computador")) {
            actionResult = deviceService.executeWindowsCommand("shutdown /s /t 5", false);
            conclusion = "Desligamento iniciado. Até breve, " + nickname + ".";
        }
        else if (lowerCommand.contains("reiniciar pc") || lowerCommand.contains("reiniciar o computador")) {
            actionResult = deviceService.executeWindowsCommand("shutdown /r /t 5", false);
            conclusion = "Reinício agendado. Volto logo, " + nickname + ".";
        }
        else if (lowerCommand.contains("aumentar volume") || lowerCommand.contains("volume up")) {
            actionResult = deviceService.executeWindowsCommand("nircmd.exe changesysvolume 10000", false);
            conclusion = "Volume aumentado, " + nickname + ". Está alto o suficiente?";
        }
        else if (lowerCommand.contains("diminuir volume") || lowerCommand.contains("volume down")) {
            actionResult = deviceService.executeWindowsCommand("nircmd.exe changesysvolume -10000", false);
            conclusion = "Volume diminuído, " + nickname + ". Melhor assim?";
        }
        else if (lowerCommand.contains("tirar print") || lowerCommand.contains("screenshot no celular")) {
            actionResult = deviceService.executeAdbCommand("shell screencap -p /sdcard/ghost-screenshot.png");
            conclusion = "Screenshot tirado no celular e salvo em /sdcard/ghost-screenshot.png, " + nickname + ".";
        }
        else if (lowerCommand.contains("bloquear tela") || lowerCommand.contains("lock screen")) {
            actionResult = deviceService.executeWindowsCommand("rundll32.exe user32.dll,LockWorkStation", false);
            conclusion = "Tela bloqueada instantaneamente, " + nickname + ". Segurança reforçada.";
        }
        else if (lowerCommand.contains("abrir whatsapp no celular")) {
            actionResult = deviceService.openAndroidApp("com.whatsapp");
            conclusion = "WhatsApp aberto no celular. Pronto, " + nickname + ".";
        }
        else if (lowerCommand.contains("pente fino") || lowerCommand.contains("diagnostico")) {
            actionResult = maintenanceService.runDiagnostics();
            conclusion = "Diagnóstico completo, " + nickname + ". Tudo verificado.";
        }
        else if (lowerCommand.contains("atualizar") || lowerCommand.contains("update")) {
            actionResult = maintenanceService.performSelfUpdate();
            conclusion = "Atualização iniciada, " + nickname + ". Sistema evoluindo.";
        }
        else {
            // Fallback para IA com confirmação inicial (Modo God mas comando desconhecido)
            String aiResponse = intelligenceService.getAiResponse(rawCommand, nickname, true, uid);
            return confirmation + "\n" + aiResponse;
        }

        // Resposta padrão para comandos diretos
        return confirmation + "\n" + actionResult + "\n" + conclusion;
    }

    private String getPrefix(String lowerCommand, String[] possiblePrefixes) {
        for (String prefix : possiblePrefixes) {
            if (lowerCommand.startsWith(prefix)) {
                return prefix;
            }
        }
        return "";
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}