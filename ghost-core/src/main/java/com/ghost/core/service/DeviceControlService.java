package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class DeviceControlService {

    // ────────────────────────────────────────────────
    // WINDOWS – Exosqueleto Completo
    // ────────────────────────────────────────────────
    public String executeWindowsCommand(String command, boolean usePowerShell) {
        log.info("Executando Windows: {} (PowerShell: {})", command, usePowerShell);
        try {
            ProcessBuilder pb;
            if (usePowerShell) {
                pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command);
            } else {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();
            return readProcessOutput(process);
        } catch (Exception e) {
            log.error("Erro crítico no exosqueleto Windows", e);
            return "Erro ao invocar o exosqueleto: " + e.getMessage();
        }
    }

    public String openWindowsApp(String appName) {
        return executeWindowsCommand("start \"\" \"" + appName + "\"", false);
    }

    // ────────────────────────────────────────────────
    // ANDROID – Link Neural via ADB
    // ────────────────────────────────────────────────
    public String executeAdbCommand(String adbArgs) {
        log.info("ADB → {}", adbArgs);
        try {
            // Refatorado para ProcessBuilder (evita depreciação do Runtime.exec)
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add("adb");
            // Divide os argumentos mantendo aspas se houver complexidade
            fullCommand.addAll(List.of(adbArgs.split(" ")));

            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            return readProcessOutput(process);
        } catch (Exception e) {
            log.error("Falha no link neural Android", e);
            return "Erro ADB: " + e.getMessage();
        }
    }

    public String openAndroidApp(String packageName) {
        return executeAdbCommand("shell monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1");
    }

    public String sendAndroidWhatsApp(String number, String message) {
        String uri = "whatsapp://send?phone=" + number + "&text=" + message.replace(" ", "%20");
        String result = executeAdbCommand("shell am start -a android.intent.action.VIEW -d \"" + uri + "\"");

        try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
        executeAdbCommand("shell input keyevent 66"); // ENTER

        return result + "\nMensagem enviada para " + number;
    }

    public String inputAndroidText(String text) {
        String escaped = text.replace("\"", "\\\"").replace(" ", "%s");
        return executeAdbCommand("shell input text \"" + escaped + "\"");
    }

    // ────────────────────────────────────────────────
    // iOS – Sinal via Webhook / Atalhos
    // ────────────────────────────────────────────────
    public String triggerIosShortcut(String shortcutName, String webhookUrl) {
        log.info("Disparando Atalho iOS: {} via {}", shortcutName, webhookUrl);
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("curl", "-s", "-X", "POST", webhookUrl + "?shortcut=" + shortcutName);
                pb.start();
            } catch (Exception e) {
                log.error("Falha no webhook iOS", e);
            }
        });
        return "Sinal enviado ao iPhone. Atalho '" + shortcutName + "' ativado.";
    }

    // ────────────────────────────────────────────────
    // MODO DE DEFESA – Lockdown Total (Nível God)
    // ────────────────────────────────────────────────
    public String activateDefenseMode() {
        log.warn("!!! MODO DE DEFESA ATIVADO – LOCKDOWN TOTAL !!!");
        StringBuilder report = new StringBuilder("Relatório de Lockdown:\n");

        report.append("[Windows]\n");
        report.append(executeWindowsCommand("netsh advfirewall set allprofiles state on", false));
        report.append(executeWindowsCommand("netsh advfirewall firewall add rule name=\"GHOST_Block_Inbound\" dir=in action=block", false));
        report.append(executeWindowsCommand("rundll32.exe user32.dll,LockWorkStation", false));
        report.append(executeWindowsCommand("taskkill /F /IM chrome.exe /IM msedge.exe /IM firefox.exe /T", false));

        report.append("\n[Android]\n");
        report.append(executeAdbCommand("shell svc wifi disable"));
        report.append(executeAdbCommand("shell svc bluetooth disable"));
        report.append(executeAdbCommand("shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"));
        report.append(executeAdbCommand("shell input keyevent 26")); 

        report.append("\n[iOS]\n");
        report.append(triggerIosShortcut("LockdownMode", "https://pushcut.io/seu-webhook-aqui"));

        return report.append("\nSistema em quarentena total.").toString();
    }

    // Método auxiliar para leitura de fluxo
    private String readProcessOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exitCode = process.waitFor();
        String prefix = (exitCode == 0) ? "Sucesso" : "Falha (cod " + exitCode + ")";
        return prefix + ":\n" + output.toString().trim();
    }
}