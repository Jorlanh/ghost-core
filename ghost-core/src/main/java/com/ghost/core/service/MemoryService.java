package com.ghost.core.service;

import com.ghost.core.model.GhostMemory;
import com.ghost.core.repository.GhostMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryService {

    private final GhostMemoryRepository ghostMemoryRepository;
    private final EmbeddingModel embeddingModel; // Spring AI injeta automaticamente (Gemini/OpenAI)

    /**
     * Salva uma nova memória após gerar o embedding.
     */
    @Transactional
    public void saveMemory(String content, String firebaseUid, String category, int importance) {
        try {
            log.info("Vetorizando memória para usuário {}: {}", firebaseUid, content.substring(0, Math.min(50, content.length())) + "...");
            float[] vector = embeddingModel.embed(content);

            GhostMemory memory = GhostMemory.builder()
                    .firebaseUid(firebaseUid)
                    .content(content)
                    .embedding(vector)
                    .importanceWeight(importance)
                    .category(category != null ? category : "auto-learned")
                    .metadata("{}")
                    .build();

            ghostMemoryRepository.save(memory);
            log.info("Memória salva. Categoria: {}, Importância: {}", memory.getCategory(), importance);
        } catch (Exception e) {
            log.error("Falha ao salvar memória semântica: {}", e.getMessage(), e);
        }
    }

    /**
     * Recupera contexto relevante para aumentar o prompt (RAG híbrido).
     */
    public String getContextForPrompt(String userPrompt, String firebaseUid) {
        try {
            float[] queryVector = embeddingModel.embed(userPrompt);

            // Usa a busca híbrida (similaridade + importância + recência)
            List<GhostMemory> memories = ghostMemoryRepository.findHybridRelevantMemories(
                    firebaseUid, queryVector, 5 // top 5
            );

            if (memories.isEmpty()) {
                return "";
            }

            String context = memories.stream()
                    .map(m -> String.format(
                            "[%s | %s | Importância %d]: %s",
                            m.getCategory(),
                            m.getCreatedAt().toString(),
                            m.getImportanceWeight(),
                            m.getContent()
                    ))
                    .collect(Collectors.joining("\n", "\n--- MEMÓRIAS RECUPERADAS ---\n", "\n--- FIM DAS MEMÓRIAS ---\n"));

            log.debug("Contexto RAG recuperado ({} memórias)", memories.size());
            return context;
        } catch (Exception e) {
            log.error("Erro na recuperação semântica: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Esquecimento seletivo (segurança).
     */
    @Transactional
    public void forget(String firebaseUid, String keyword) {
        try {
            ghostMemoryRepository.deleteMemoriesByKeyword(firebaseUid, keyword);
            log.warn("Memórias contendo '{}' foram eliminadas para usuário {}", keyword, firebaseUid);
        } catch (Exception e) {
            log.error("Falha no esquecimento seletivo: {}", e.getMessage(), e);
        }
    }
}