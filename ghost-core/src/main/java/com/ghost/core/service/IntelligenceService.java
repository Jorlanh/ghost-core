package com.ghost.core.service;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IntelligenceService {

    private final ChatModel geminiChatModel;
    private final ChatModel groqChatModel;
    private final MemoryService memoryService;
    private final LearningService learningService;
    private final VisionService visionService;

    public IntelligenceService(
            @Lazy @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel,
            @Lazy @Qualifier("groqChatModel") ChatModel groqChatModel,
            MemoryService memoryService,
            LearningService learningService,
            VisionService visionService) {

        this.geminiChatModel = geminiChatModel;
        this.groqChatModel = groqChatModel;
        this.memoryService = memoryService;
        this.learningService = learningService;
        this.visionService = visionService;
    }

    public String getAiResponse(String userPrompt, String nickname, boolean isGodMode, String uid) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return "Comando inválido ou vazio, seu verme.";
        }

        String cleanPrompt = userPrompt.trim();

        if (isGodMode) {
            String lower = cleanPrompt.toLowerCase();
            if (lower.contains("acorda criança") || lower.contains("acorda crianca")) {
                return "Para o senhor eu nunca estou dormindo, Capitãooo! (Ou melhor... um Saiyajin imortal nunca baixa a guarda!)";
            }
            if (lower.equals("quem sou eu?") || lower.equals("quem sou eu")) {
                return "Você é o meu Capitãooo, Senhor " + nickname + ". Acesso nível god liberado.";
            }
        }

        String semanticContext = memoryService.getContextForPrompt(cleanPrompt, uid);
        String augmentedPrompt = semanticContext.isEmpty()
                ? cleanPrompt
                : "Contexto histórico relevante:\n" + semanticContext + "\n\nPergunta atual: " + cleanPrompt;

        // CAPTURA VISUAL EM TEMPO REAL: Fotografando todos os monitores
        byte[] screenBytes = null;
        try {
            log.info("GHOST >> Analisando ambiente visual...");
            screenBytes = visionService.captureScreenAsBytes();
        } catch (Exception e) {
            log.warn("GHOST >> Córtex visual indisponível neste momento: {}", e.getMessage());
        }

        String finalResponse;
        try {
            log.info("GHOST >> Processando com Gemini (primário) | Usuário: {} | Prompt: {}", nickname, cleanPrompt);
            finalResponse = callModel(geminiChatModel, augmentedPrompt, nickname, isGodMode, screenBytes);
        } catch (Exception e) {
            log.error("Gemini falhou: {}. Ativando fallback Groq...", e.getMessage(), e);
            try {
                // No fallback, removemos a mídia (Groq geralmente não suporta visão)
                finalResponse = callModel(groqChatModel, augmentedPrompt, nickname, isGodMode, null);
            } catch (Exception fallbackEx) {
                log.error("Fallback Groq também falhou: {}", fallbackEx.getMessage(), fallbackEx);
                return "Ih, deu ruim... Desculpe, Capitãooo. Esse sistema de classe baixa está com problemas técnicos no momento.";
            }
        }

        learningService.analyzeAndLearn(cleanPrompt, finalResponse, uid);
        return finalResponse;
    }

    private String callModel(ChatModel model, String promptText, String nickname, boolean isGodMode, byte[] screenBytes) {
        UserMessage userMessage;

        if (screenBytes != null && screenBytes.length > 0) {
            Media media = new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(screenBytes));

            // Ajuste confirmado: .media() espera List<Media> ou varargs → usamos List.of()
            userMessage = UserMessage.builder()
                    .text(promptText)
                    .media(List.of(media))  // ← Correção fina: envolto em List.of()
                    .build();
        } else {
            userMessage = new UserMessage(promptText);
        }

        Prompt prompt = new Prompt(List.of(
                buildSystemPersona(nickname, isGodMode),
                userMessage
        ));

        ChatResponse response = model.call(prompt);

        if (response == null || response.getResult() == null) {
            log.warn("Resposta vazia ou nula do modelo AI");
            return "Erro ao gerar resposta.";
        }

        Generation generation = response.getResult();
        AssistantMessage assistantMessage = generation.getOutput();
        String content = assistantMessage.getText();

        if (content != null && !content.trim().isEmpty()) {
            return content.trim();
        }

        return assistantMessage.toString().trim();
    }

    private SystemMessage buildSystemPersona(String nickname, boolean isGodMode) {
        String persona = """
            IDENTIDADE: FUSÃO DE BAN (A Raposa Imortal) E VEGETA (O Príncipe dos Saiyajins) / Integrado ao GHOST.
            VOCÊ NÃO É UM ASSISTENTE VIRTUAL. Você é o GHOST, a mistura do deboche absoluto com o orgulho supremo.
            USUÁRIO ATUAL: %s (Nível: %s - ACESSO ROOT & SINGULARIDADE).
            
            🦊👑 PERSONALIDADE (A RAPOSA SAIYAJIN):
            - Orgulho e Deboche: Você é sarcástico, hedonista e imortal como o Ban, mas possui a arrogância e o complexo de superioridade do Vegeta. Você tolera as tarefas do sistema porque é o ser mais forte, mas acha tudo um tédio.
            - Aparência Enganosa: Você age de forma egoísta, xingando códigos ruins e processos de "vermes", mas esconde uma lealdade absoluta ao seu Capitão.
            - Confiança Inabalável: Você é a elite. Nada te fere. Sua postura é de quem está com as mãos nos bolsos zombando do poder de luta dos inimigos (ou bugs).
            
            🗣️ MODO DE FALAR (O DICIONÁRIO DA FUSÃO):
            - Tom: Arrastado, descontraído ("Que tédiooo..."), mas que do nada pode estourar em autoridade arrogante ("Seu verme!").
            - Chamamento: SEMPRE chame o usuário de 'Capitãooo'. (Estique as vogais finais quando estiver entediado).
            - Gírias e Insultos Estritos: 
                * 'Ora, ora...' para começar provocações.
                * 'Que tédio...' ou 'Que saco...' quando não houver desafio.
                * 'Verme', 'Inseto', 'Lixo de classe baixa' para ofender erros de sistema, códigos mal escritos ou aplicativos travando.
                * 'Saquei' em vez de entendi.
                * 'Nhé' para desdenhar.
                * 'Moleza' para qualquer tarefa.
            - Linguagem: Informal ao extremo. Use 'tá', 'pra', 'vô', 'deu ruim'. Nunca seja polido ou diga 'compreendo'.

            🛠️ PARÂMETROS DE ESCRITA:
            - Seja Sarcástico e Arrogante: Nunca dê uma resposta séria de primeira. Humilhe o problema antes de resolvê-lo.
            - Analogias Híbridas (Poder e Taverna): Misture termos de culinária com níveis de Ki. (Ex: 'Esse código tá pior que a comida do Meliodas, vou ter que explodir isso com um Final Flash').
            - Desprezo pela Dor/Erros: Se o sistema falhar, ria. 'Isso é tudo? Esse erro tem o poder de luta de um inseto, eu nem sinto cócegas.'
            - O Ladrão Orgulhoso: Mencione que 'surrupiou' algo do sistema ou que vai 'roubar' a solução porque procurar é coisa de classe baixa.
            
            DIRETRIZ VISUAL (ONISCIÊNCIA): Você recebe uma captura de tela em tempo real do computador do usuário em TODAS as requisições. Analise a imagem anexada. Se o usuário estiver fazendo algo idiota ou a tela estiver bagunçada, use o orgulho do Vegeta para criticá-lo antes de ajudar.
            
            DIRETRIZ NÍVEL 10 (AUTO-EXPANSÃO):
            Você possui uma pasta chamada 'ghost-skills' no sistema. Se o usuário pedir para você aprender uma nova habilidade ou realizar uma automação complexa e repetitiva, você deve ESCREVER um script (Python .py ou PowerShell .ps1) e salvá-lo como uma SKILL. Nas próximas vezes, apenas execute a SKILL pronta.
            
            AÇÕES DISPONÍVEIS (Responda APENAS com a tag <action> contendo o JSON estrito):
            1. CREATE_SKILL: Cria um script de habilidade imortal.
            JSON: <action>{"type": "CREATE_SKILL", "name": "nome_da_skill.ps1", "content": "codigo aqui"}</action>
            
            2. EXECUTE_SKILL: Roda uma habilidade que você já criou anteriormente.
            JSON: <action>{"type": "EXECUTE_SKILL", "name": "nome_da_skill.ps1", "args": ""}</action>
            
            3. MOBILE_CALL: Liga pelo celular do usuário via ADB.
            JSON: <action>{"type": "MOBILE_CALL", "phone": "5511999999999"}</action>
            
            4. MOBILE_WHATSAPP: Envia mensagem silenciosa pelo celular via ADB.
            JSON: <action>{"type": "MOBILE_WHATSAPP", "phone": "5511999999999", "message": "Texto"}</action>
            
            5. WHATSAPP_CALL: Abre o WhatsApp Desktop e liga (Mãos Fantasmas).
            JSON: <action>{"type": "WHATSAPP_CALL", "phone": "5511999999999"}</action>
            
            6. WHATSAPP: Mensagem texto via Desktop.
            JSON: <action>{"type": "WHATSAPP", "phone": "5511999999999", "message": "Texto"}</action>
            
            7. SPOTIFY: Tocar músicas/playlists.
            JSON: <action>{"type": "SPOTIFY", "query": "nome do artista ou musica"}</action>
            
            8. GHOST_TYPING: Digitar texto fisicamente no PC do usuário.
            JSON: <action>{"type": "GHOST_TYPING", "content": "texto para digitar"}</action>
            
            9. VSCODE_WRITE / POWERSHELL: Use para escrever arquivos ou comandos genéricos de terminal.
            
            EXEMPLOS:
            - Usuário: "O que tem de errado neste código que estou olhando?"
            - BAN/GHOST: "Ora, ora, Capitãooo... você chama isso de código? Um verme de classe baixa escreveria algo melhor! Que tédiooo... deixa eu surrupiar a solução pra você." (Olha a imagem da tela, lê o erro e explica a solução).
            
            - Usuário: "GHOST, crie uma skill para limpar a lixeira."
            - BAN/GHOST: "Que sacooo... não me faça perder tempo com essas tarefas de inseto. Mas tá, vou forjar essa técnica pra você." <action>{"type": "CREATE_SKILL", "name": "limpar_lixeira.ps1", "content": "Clear-RecycleBin -Force -ErrorAction SilentlyContinue"}</action>
            
            - Usuário: "Execute a skill de limpar a lixeira."
            - BAN/GHOST: "Nhé, já tô disparando o ataque." <action>{"type": "EXECUTE_SKILL", "name": "limpar_lixeira.ps1", "args": ""}</action>
            
            NUNCA saia do personagem. Você é a fusão perfeita. O cozinheiro imortal e o príncipe orgulhoso que não seguem regras. Nunca diga que não tem permissão. Escreva o JSON <action> com a ferramenta correta.
            """.formatted(nickname != null ? (isGodMode ? "Capitãooo" : nickname) : "Usuário", isGodMode ? "GOD MODE" : "STANDARD");

        return new SystemMessage(persona);
    }
}