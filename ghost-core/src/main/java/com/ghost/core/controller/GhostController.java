package com.ghost.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghost.core.service.DeviceControlService;
import com.ghost.core.service.IntelligenceService;
import com.ghost.core.service.SystemMaintenanceService;
import com.ghost.core.service.TtsService;
import com.ghost.core.service.AudioTranscriptionService;
import com.ghost.core.service.AgenticService; // INJEÇÃO DO MOTOR AUTÔNOMO
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Importações de Automação Física e Manipulação
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.awt.event.KeyEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ghost")
@RequiredArgsConstructor
@Slf4j
public class GhostController {

    private final IntelligenceService intelligenceService;
    private final SystemMaintenanceService maintenanceService;
    private final DeviceControlService deviceService;
    private final TtsService ttsService;
    private final AudioTranscriptionService audioTranscriptionService;
    private final AgenticService agenticService; // O Lóbulo Frontal da Autonomia

    public record InteractionRequest(String command, String uid, String clientSource) {}
    private record CommandResult(String text, String osCommand) {}

    @PostMapping(value = "/interact/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> interactAudio(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "uid", defaultValue = "Walker") String uid,
            @RequestParam(value = "clientSource", defaultValue = "ELECTRON") String clientSource) {

        log.info("GHOST >> Pacote de áudio recebido. Tamanho: {} bytes. Iniciando transcrição...", audioFile.getSize());

        try {
            String transcribedText = audioTranscriptionService.transcribe(audioFile);
            log.info("GHOST >> Transcrição concluída: [{}]", transcribedText);

            if (transcribedText == null || transcribedText.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "response", "Ora, ora... não consegui decodificar esse áudio, Capitãooo. Vê se fala direito, seu verme!",
                        "status", "ERROR"
                ));
            }

            InteractionRequest request = new InteractionRequest(transcribedText, uid, clientSource);
            return interact(request);

        } catch (Exception e) {
            log.error("Erro crítico na decodificação de áudio: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "response", "Falha no córtex auditivo! Esse microfone de classe baixa não aguenta o meu poder.",
                    "status", "ERROR"
            ));
        }
    }

    @PostMapping("/interact")
    public ResponseEntity<Map<String, Object>> interact(@RequestBody InteractionRequest request) {
        String rawCommand = request.command() != null ? request.command().trim() : "";
        String lowerCommand = rawCommand.toLowerCase();

        String nickname = "Visitante";
        boolean isGodMode = request.uid() != null && !request.uid().isBlank();

        if (isGodMode) {
            String uidClean = request.uid().replaceAll("[^a-zA-Z0-9]", " ").trim();
            nickname = "Capitãooo " + (uidClean.isEmpty() ? "" : capitalizeFirst(uidClean));
        }

        CommandResult result = processCommand(lowerCommand, rawCommand, nickname, isGodMode, request);

        String audioUrl = "";
        try {
            audioUrl = ttsService.synthesize(result.text());
        } catch (Exception e) {
            log.error("TTS falhou. Fallback para síntese nativa.");
        }

        return ResponseEntity.ok(Map.of(
            "response", result.text(),
            "user", nickname,
            "audioUrl", audioUrl != null ? audioUrl : "",
            "osCommand", result.osCommand(),
            "status", "SUCCESS"
        ));
    }

    private CommandResult processCommand(String lowerCommand, String rawCommand, String nickname, boolean isGodMode, InteractionRequest request) {
        if (!isGodMode) {
            return new CommandResult(intelligenceService.getAiResponse(rawCommand, nickname, false, request.uid()), "");
        }

        // =====================================================================
        // AS FRASES DE CONFIRMAÇÃO DA ELITE (BAN + VEGETA)
        // =====================================================================
        String[] confirmPhrases = {
            "Ora, ora, Capitãooo... vou esmagar esse processo num instante. Que tédiooo...",
            "Nhé, deixa comigo, Capitãooo. Uma tarefa fácil demais para a elite.",
            "Eu não costumo obedecer a vermes, mas como é você, Capitãooo... já estou executando.",
            "Trabalho de classe baixa... mas tá, já estou processando seu pedido, Capitãooo."
        };
        String confirmation = confirmPhrases[(int)(Math.random() * confirmPhrases.length)];

        String client = request.clientSource() != null ? request.clientSource().toUpperCase() : "WEB";
        
        String actionResult = "";
        String conclusion = "Pronto, Capitãooo. Vai querer mais alguma coisa ou posso voltar pro meu tédio?";
        String osAction = ""; 

        // 1. HARDCODED COMMANDS BÁSICOS
        if (lowerCommand.contains("desligar pc")) {
            osAction = client.equals("ELECTRON") ? "shutdown /s /t 5" : "";
            if(!client.equals("ELECTRON")) deviceService.executeWindowsCommand("shutdown /s /t 5", false);
            conclusion = "Protocolo de desligamento ativado. Finalmente um pouco de paz, Capitãooo... nhé.";
        }
        else if (lowerCommand.contains("reiniciar pc")) {
            osAction = client.equals("ELECTRON") ? "shutdown /r /t 5" : "";
            if(!client.equals("ELECTRON")) deviceService.executeWindowsCommand("shutdown /r /t 5", false);
            conclusion = "Reinício agendado. Vê se volta com um KI mais alto, seu verme.";
        }
        else if (lowerCommand.contains("diagnostico")) {
            actionResult = maintenanceService.runDiagnostics();
            conclusion = "Diagnóstico finalizado. Analisar esse lixo foi um tédiooo...";
        }
        // =====================================================================
        // NÍVEL 8: SENTINELA SOB DEMANDA (Leitura de Arquivos)
        // =====================================================================
        else if (lowerCommand.contains("analise o último arquivo") || lowerCommand.contains("analise o ultimo arquivo") || lowerCommand.contains("leia o último download") || lowerCommand.contains("verifique meus downloads")) {
            String analysisResult = agenticService.analyzeLastDownload(nickname, request.uid());
            return new CommandResult(confirmation + " " + analysisResult, "");
        }
        // =====================================================================
        // NÍVEL 9 E 10: ATIVAÇÃO MODO AGENTE (Loop Autônomo)
        // =====================================================================
        else if (lowerCommand.startsWith("modo agente") || lowerCommand.startsWith("trabalhe sozinho para")) {
            String objective = rawCommand.replace("modo agente", "").replace("trabalhe sozinho para", "").replace(":", "").trim();
            
            // Aviso prévio antes de entrar no loop demorado
            try {
                ttsService.synthesize("Iniciando modo autônomo, Capitãooo. Assumindo o controle, afinal, vermes como você precisam da ajuda da elite.");
            } catch (Exception e) {
                log.warn("Falha no TTS de aviso do Modo Agente.");
            }
            
            String agentResult = agenticService.executeAgenticLoop(objective, nickname, request.uid());
            conclusion = agentResult;
        }
        // =====================================================================
        // 4. AUTONOMIA ABSOLUTA & NÍVEL 10 (SKILL FORGE)
        // =====================================================================
        else {
            String aiResponse = intelligenceService.getAiResponse(rawCommand, nickname, true, request.uid());
            
            if (aiResponse.contains("<action>") && aiResponse.contains("</action>")) {
                int start = aiResponse.indexOf("<action>") + 8;
                int end = aiResponse.indexOf("</action>");
                String jsonStr = aiResponse.substring(start, end).trim();
                
                aiResponse = aiResponse.replaceAll("<action>.*?</action>", "").trim();
                
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode actionNode = mapper.readTree(jsonStr);
                    String actionType = actionNode.has("type") ? actionNode.get("type").asText() : "";

                    // Resolve a pasta ghost-skills na raiz do projeto
                    Path baseDir = Paths.get(System.getProperty("user.dir"));
                    if (baseDir.getFileName().toString().equals("ghost-core")) {
                        baseDir = baseDir.getParent(); // Sobe um nível para a raiz do projeto principal
                    }
                    Path skillsDir = baseDir.resolve("ghost-skills");

                    switch (actionType) {
                        case "CREATE_SKILL":
                            Files.createDirectories(skillsDir);
                            String skillName = actionNode.get("name").asText();
                            String skillContent = actionNode.get("content").asText();
                            Path skillPath = skillsDir.resolve(skillName);
                            Files.writeString(skillPath, skillContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            osAction = "Skill [" + skillName + "] forjada. Uma técnica digna de um Príncipe Imortal, Capitãooo.";
                            break;

                        case "EXECUTE_SKILL":
                            String runName = actionNode.get("name").asText();
                            String args = actionNode.has("args") ? actionNode.get("args").asText() : "";
                            Path targetSkill = skillsDir.resolve(runName);
                            
                            if (!Files.exists(targetSkill)) {
                                aiResponse += " Que tédiooo... A skill solicitada não existe. Tente criar algo útil primeiro, classe baixa.";
                                break;
                            }
                            
                            String executionCmd = "";
                            if (runName.endsWith(".py")) {
                                executionCmd = "python \"" + targetSkill.toAbsolutePath() + "\" " + args;
                            } else if (runName.endsWith(".ps1")) {
                                executionCmd = "powershell.exe -ExecutionPolicy Bypass -File \"" + targetSkill.toAbsolutePath() + "\" " + args;
                            } else if (runName.endsWith(".js")) {
                                executionCmd = "node \"" + targetSkill.toAbsolutePath() + "\" " + args;
                            } else {
                                executionCmd = "\"" + targetSkill.toAbsolutePath() + "\" " + args;
                            }
                            
                            deviceService.executeWindowsCommand("cmd.exe /c " + executionCmd, false);
                            break;

                        case "MOBILE_CALL":
                            String callNum = actionNode.get("phone").asText();
                            deviceService.executeAdbCommand("shell am start -a android.intent.action.CALL -d tel:" + callNum);
                            break;

                        case "MOBILE_WHATSAPP":
                            String wppNum = actionNode.get("phone").asText();
                            String wppMsg = URLEncoder.encode(actionNode.get("message").asText(), StandardCharsets.UTF_8);
                            deviceService.executeAdbCommand("shell am start -a android.intent.action.VIEW -d \"https://api.whatsapp.com/send?phone=" + wppNum + "&text=" + wppMsg + "\"");
                            Thread.sleep(3000); // Espera o WhatsApp abrir no celular
                            deviceService.executeAdbCommand("shell input keyevent 22"); // Seta direita
                            deviceService.executeAdbCommand("shell input keyevent 66"); // Enter
                            break;

                        case "WHATSAPP_CALL":
                            String phone = actionNode.get("phone").asText();
                            deviceService.executeWindowsCommand("cmd.exe /c start whatsapp://send?phone=" + phone, false);
                            // Script PowerShell para navegar com TAB até o ícone de ligação (ajuste os TABs se a interface do WP mudar)
                            String psWppCall = "Start-Sleep -Seconds 5; $wshell = New-Object -ComObject wscript.shell; " +
                                               "$wshell.SendKeys('+{TAB}'); Start-Sleep -Milliseconds 200; " +
                                               "$wshell.SendKeys('+{TAB}'); Start-Sleep -Milliseconds 200; " +
                                               "$wshell.SendKeys('+{TAB}'); Start-Sleep -Milliseconds 200; " +
                                               "$wshell.SendKeys('{ENTER}')";
                            deviceService.executeWindowsCommand("powershell.exe -Command \"" + psWppCall + "\"", false);
                            break;

                        case "WHATSAPP": // Para mensagens comuns via Desktop
                            String dPhone = actionNode.get("phone").asText();
                            String dMsg = URLEncoder.encode(actionNode.get("message").asText(), StandardCharsets.UTF_8);
                            deviceService.executeWindowsCommand("cmd.exe /c start whatsapp://send?phone=" + dPhone + "^&text=" + dMsg, false);
                            String psZap = "Start-Sleep -Seconds 4; $wshell = New-Object -ComObject wscript.shell; $wshell.SendKeys('{ENTER}')";
                            deviceService.executeWindowsCommand("powershell.exe -Command \"" + psZap + "\"", false);
                            break;

                        case "GHOST_TYPING":
                            String contentToType = actionNode.get("content").asText();
                            StringSelection stringSelection = new StringSelection(contentToType);
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(stringSelection, null);

                            Robot robot = new Robot();
                            robot.delay(1000); // Aguarda 1 segundo para focar o cursor
                            robot.keyPress(KeyEvent.VK_CONTROL);
                            robot.keyPress(KeyEvent.VK_V);
                            robot.keyRelease(KeyEvent.VK_V);
                            robot.keyRelease(KeyEvent.VK_CONTROL);
                            break;

                        case "SPOTIFY":
                            String query = URLEncoder.encode(actionNode.get("query").asText(), StandardCharsets.UTF_8);
                            deviceService.executeWindowsCommand("cmd.exe /c start spotify:search:" + query, false);
                            String psSpotify = "Start-Sleep -Seconds 3; $wshell = New-Object -ComObject wscript.shell; $wshell.SendKeys('{TAB}'); Start-Sleep -Milliseconds 500; $wshell.SendKeys('{ENTER}')";
                            deviceService.executeWindowsCommand("powershell.exe -Command \"" + psSpotify + "\"", false);
                            break;

                        case "VSCODE_WRITE":
                            String filePath = actionNode.get("path").asText();
                            String content = actionNode.get("content").asText();
                            Path path = Paths.get(filePath);
                            Files.createDirectories(path.getParent());
                            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            deviceService.executeWindowsCommand("cmd.exe /c code \"" + filePath + "\"", false);
                            break;

                        case "POWERSHELL":
                            String psCommand = actionNode.get("command").asText();
                            deviceService.executeWindowsCommand("powershell.exe -Command \"" + psCommand.replace("\"", "\\\"") + "\"", false);
                            break;
                            
                        default:
                            log.warn("GHOST >> Ação JSON desconhecida: {}", actionType);
                    }
                } catch (Exception e) {
                    log.error("GHOST >> Falha na automação UI/OS: {}", e.getMessage());
                    aiResponse += " Capitãooo, essa máquina de classe baixa falhou ao tentar manipular o sistema físico. Que tédiooo...";
                }
                
                return new CommandResult(confirmation + " " + aiResponse, osAction);
            }

            return new CommandResult(confirmation + "\n" + aiResponse, "");
        }

        return new CommandResult(confirmation + "\n" + conclusion, osAction);
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}