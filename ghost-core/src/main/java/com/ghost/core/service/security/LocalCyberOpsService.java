package com.ghost.core.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LocalCyberOpsService {

    public String runLocalPortScan(String targetIp) {
        String target = targetIp == null || targetIp.isBlank() ? "127.0.0.1" : targetIp.trim();
        if (!isAllowedTarget(target)) {
            return "Alvo recusado. Cyber-Ops aceita apenas localhost, rede privada ou alvo autorizado em GHOST_ALLOWED_SCAN_TARGETS.";
        }

        log.info("[CYBER-OPS] Defensive Nmap scan started for {}", target);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "--rm", "kalilinux/kali-rolling", "nmap", "-sV", "--top-ports", "100", target
        );

        try {
            Process process = processBuilder.redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.lines().collect(Collectors.joining("\n"));
                process.waitFor();
                return output;
            }
        } catch (Exception e) {
            log.warn("[CYBER-OPS] Docker/Nmap unavailable: {}", e.getMessage());
            return "Nmap via Docker indisponivel: " + e.getMessage();
        }
    }

    public String runWindowsPortSnapshot() {
        return runCommand(List.of("powershell.exe", "-NoProfile", "-Command",
                "Get-NetTCPConnection -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess | Sort-Object LocalPort | ConvertTo-Json -Compress"));
    }

    public Map<String, Object> defensiveSummary(String target) {
        String normalizedTarget = target == null || target.isBlank() ? "127.0.0.1" : target.trim();
        boolean allowed = isAllowedTarget(normalizedTarget);
        return Map.of(
                "mode", "WHITE_HAT_DEFENSIVE",
                "target", normalizedTarget,
                "allowed", allowed,
                "scan", allowed ? runLocalPortScan(normalizedTarget) : "Alvo bloqueado pela politica local.",
                "localListeners", runWindowsPortSnapshot()
        );
    }

    private String runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.lines().collect(Collectors.joining("\n"));
                process.waitFor();
                return output;
            }
        } catch (Exception e) {
            return "Falha ao executar auditoria local: " + e.getMessage();
        }
    }

    private boolean isAllowedTarget(String target) {
        try {
            String allowed = System.getenv("GHOST_ALLOWED_SCAN_TARGETS");
            if (allowed != null && !allowed.isBlank()) {
                for (String item : allowed.split(",")) {
                    if (target.equalsIgnoreCase(item.trim())) return true;
                }
            }

            InetAddress address = InetAddress.getByName(target);
            String host = address.getHostAddress();
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || host.startsWith("192.168.")
                    || host.startsWith("10.")
                    || host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
        } catch (Exception e) {
            return false;
        }
    }
}
