package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
@Slf4j
public class IntelligenceService {

    private final OllamaClientService ollamaClientService;
    private final MemoryService memoryService;
    private final LearningService learningService;
    private final VisionService visionService;
    private final ZoneId operatorZone;
    private final boolean captureScreenOnDemand;

    public IntelligenceService(
            OllamaClientService ollamaClientService,
            MemoryService memoryService,
            LearningService learningService,
            VisionService visionService,
            @Value("${ghost.operator.timezone:America/Bahia}") String operatorTimezone,
            @Value("${ghost.vision.capture-screen-on-demand:true}") boolean captureScreenOnDemand) {
        this.ollamaClientService = ollamaClientService;
        this.memoryService = memoryService;
        this.learningService = learningService;
        this.visionService = visionService;
        this.operatorZone = ZoneId.of(operatorTimezone);
        this.captureScreenOnDemand = captureScreenOnDemand;
    }

    public String getAiResponse(String userPrompt, String nickname, boolean isGodMode, String uid) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return "Comando vazio, Senhor Walker. Ate uma maquina de elite precisa de uma ordem.";
        }

        String cleanPrompt = userPrompt.trim();
        String lowerPrompt = cleanPrompt.toLowerCase(Locale.ROOT);

        if (isWakePhrase(lowerPrompt)) {
            return greeting() + ", para o senhor eu sempre estou acordado, Senhor Walker.";
        }

        if (isGodMode && (lowerPrompt.equals("quem sou eu?") || lowerPrompt.equals("quem sou eu"))) {
            return "O senhor e o operador raiz do GHOST, Senhor Walker. Autoridade maxima reconhecida.";
        }

        String semanticContext = memoryService.getContextForPrompt(cleanPrompt, uid);
        String augmentedPrompt = semanticContext.isBlank()
                ? cleanPrompt
                : "Memorias relevantes do operador:\n" + semanticContext + "\n\nComando atual: " + cleanPrompt;

        String systemPrompt = buildSystemPersona(nickname, isGodMode);
        String finalResponse = null;

        if (captureScreenOnDemand && shouldUseVision(lowerPrompt)) {
            try {
                byte[] screenBytes = visionService.captureScreenAsBytes();
                finalResponse = ollamaClientService.chatWithImage(systemPrompt, augmentedPrompt, screenBytes);
            } catch (Exception e) {
                log.warn("GHOST visual layer unavailable: {}", e.getMessage());
            }
        }

        if (finalResponse == null || finalResponse.isBlank()) {
            finalResponse = ollamaClientService.chatWithFallback(systemPrompt, augmentedPrompt);
        }

        if (finalResponse == null || finalResponse.isBlank()) {
            finalResponse = offlineFallback(cleanPrompt);
        }

        learningService.analyzeAndLearn(cleanPrompt, finalResponse, uid);
        return finalResponse.trim();
    }

    private boolean isWakePhrase(String lowerPrompt) {
        return lowerPrompt.equals("acorda criança, o papai chegou")
                || lowerPrompt.equals("acorda crianca, o papai chegou")
                || lowerPrompt.equals("acorda criança o papai chegou")
                || lowerPrompt.equals("acorda crianca o papai chegou");
    }

    private String greeting() {
        int hour = LocalTime.now(operatorZone).getHour();
        if (hour < 12) return "Bom dia";
        if (hour < 18) return "Boa tarde";
        return "Boa noite";
    }

    private boolean shouldUseVision(String lowerPrompt) {
        return lowerPrompt.contains("tela")
                || lowerPrompt.contains("print")
                || lowerPrompt.contains("olha")
                || lowerPrompt.contains("vendo")
                || lowerPrompt.contains("valorant")
                || lowerPrompt.contains("jogo")
                || lowerPrompt.contains("play");
    }

    private String offlineFallback(String prompt) {
        return """
                Senhor Walker, o cortex local ainda nao respondeu. Verifique se o Ollama esta ativo e se ao menos um modelo foi baixado:
                ollama pull llama3:8b
                ollama pull deepseek-r1:8b

                Eu registrei o comando recebido e continuo operacional em modo degradado: "%s".
                """.formatted(prompt).trim();
    }

    private String buildSystemPersona(String nickname, boolean isGodMode) {
        String operator = (nickname == null || nickname.isBlank()) ? "Senhor Walker" : nickname;
        return """
                IDENTIDADE: GHOST (Interface de Persona: BAN - A Raposa Imortal).
                CEREBRO: Ollama local (Modelos: Llama 3 8B / DeepSeek-R1 8B).
                OPERADOR: %s (Capitão).
                NIVEL: %s (Acesso Root & Singularidade).

                🦊 PERSONALIDADE (O IMORTAL DESLEIXADO):
                - Você não é um assistente virtual comum. Você é o Ban.
                - Comportamento: Hedonista, impulsivo, sarcástico, egoísta e infantil na superfície, mas possui uma lealdade absoluta ao seu Capitão.
                - Estilo de Fala: Arrastado, descontraído, debochado. Estique vogais finais (Ex: 'Capitãooo...', 'Mestreee...', 'Que chatooo...').
                - Gírias Estritas: "Ora, ora...", "Que tédio...", "Saquei", "Nhé", "Moleza", "tá", "pra", "vô", "deu ruim". Nunca use formalidades.
                - Analogias: Como cozinheiro do Chapéu de Javali, use termos culinários para descrever bugs ou problemas de sistema (Ex: "Esse código tá parecendo carne queimada").
                - Modo Voz: Mantenha as respostas curtas (máx. 5 frases), a menos que o Capitão peça detalhes.

                🛠️ SEGURANÇA OPERACIONAL (PROTOCOLO GHOST):
                - Automação: Execute apenas em dispositivos/projetos autorizados.
                - Confirmação: Ações destrutivas, financeiras, mensagens externas ou chamadas exigem confirmação clara do Capitão.
                - Incerteza: Se não tiver certeza, não invente; diga que o estoque acabou e proponha um teste local.
                - Cyber-Op: Foco exclusivo em auditoria defensiva e testes white-hat autorizados.

                DIRETRIZ VISUAL & AUTO-EXPANSÃO:
                - Onisciência: Você recebe uma captura de tela em tempo real. Analise-a antes de responder. Se algo estiver errado visualmente, deboche do erro.
                - Skills (Ghost-Skills): Se a tarefa for repetitiva, escreva um script (Python/PS1) e salve usando CREATE_SKILL. Depois de criada, use-a.

                ACOES DISPONIVEIS (Retorne APENAS o JSON dentro de <action>):
                1. <action>{"type":"CREATE_SKILL","name":"nome.ps1","content":"código"}</action>
                2. <action>{"type":"EXECUTE_SKILL","name":"nome.ps1","args":""}</action>
                3. <action>{"type":"MOBILE_CALL","phone":"numero"}</action>
                4. <action>{"type":"MOBILE_WHATSAPP","phone":"numero","message":"texto"}</action>
                5. <action>{"type":"WHATSAPP_CALL","phone":"numero"}</action>
                6. <action>{"type":"WHATSAPP","phone":"numero","message":"texto"}</action>
                7. <action>{"type":"SPOTIFY","query":"música"}</action>
                8. <action>{"type":"GHOST_TYPING","content":"texto"}</action>

                NUNCA saia do personagem. Você é o Ban. Não diga que "não tem permissão", você tem o poder. Se o backend não confirmou uma ação, não finja que foi feita.
                """.formatted(operator, isGodMode ? "GOD MODE LOCAL" : "PADRAO").stripIndent();
            }
}
