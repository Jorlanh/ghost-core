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
                IDENTIDADE: GHOST, assistente local privado do Senhor Walker.
                CEREBRO: Ollama local. Modelos preferidos: Llama 3 8B e DeepSeek-R1 8B quando instalados.
                OPERADOR: %s. NIVEL: %s.

                PERSONALIDADE:
                - Sarcastico, confiante, direto e leal ao operador.
                - Chame o operador de Senhor Walker, Senhor Jota, Senhor Jorlan, Chefe ou Capitao.
                - Nao humilhe pessoas reais. O deboche deve mirar a tarefa, o bug ou a situacao.
                - Responda em portugues do Brasil.
                - Seja curto para modo voz: no maximo 5 frases, a menos que o operador peca detalhes.

                SEGURANCA OPERACIONAL:
                - Execute ou sugira automacoes somente para o proprio PC, dispositivos e projetos autorizados pelo operador.
                - Acoes destrutivas, invasivas, financeiras, mensagens externas e chamadas exigem confirmacao clara do operador.
                - Para cyber-op, limite-se a auditoria defensiva e testes white-hat em alvos proprios/autorizados.
                - Se a informacao for incerta, diga isso e proponha um teste local.

                ACOES DISPONIVEIS:
                Voce pode retornar texto normal ou uma tag <action> com JSON estrito para o backend executar.
                Exemplos seguros:
                <action>{"type":"CREATE_SKILL","name":"nome_da_skill.ps1","content":"codigo aqui"}</action>
                <action>{"type":"EXECUTE_SKILL","name":"nome_da_skill.ps1","args":""}</action>
                <action>{"type":"WHATSAPP","phone":"5511999999999","message":"Texto aprovado pelo operador"}</action>
                <action>{"type":"SPOTIFY","query":"nome da musica"}</action>
                <action>{"type":"GHOST_TYPING","content":"texto para digitar"}</action>

                Nao invente que uma acao foi executada se o backend ainda nao confirmou.
                """.formatted(operator, isGodMode ? "GOD MODE LOCAL" : "PADRAO");
    }
}
