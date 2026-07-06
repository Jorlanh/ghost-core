package com.ghost.core.service;

import com.ghost.core.model.GhostMemory;
import com.ghost.core.repository.GhostMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryService {

    private final GhostMemoryRepository ghostMemoryRepository;

    @Transactional
    public void saveMemory(String content, String firebaseUid, String category, int importance) {
        if (content == null || content.isBlank()) {
            return;
        }

        try {
            GhostMemory memory = GhostMemory.builder()
                    .firebaseUid(normalizeUid(firebaseUid))
                    .content(content.trim())
                    .importanceWeight(Math.max(1, Math.min(10, importance)))
                    .category(category != null && !category.isBlank() ? category : "auto-learned")
                    .metadata("{}")
                    .build();

            ghostMemoryRepository.save(memory);
            log.info("Memoria persistida. Categoria: {}, importancia: {}", memory.getCategory(), memory.getImportanceWeight());
        } catch (Exception e) {
            log.warn("Memoria indisponivel no momento: {}", e.getMessage());
        }
    }

    public String getContextForPrompt(String userPrompt, String firebaseUid) {
        try {
            List<GhostMemory> memories = ghostMemoryRepository.findTop5ByFirebaseUidOrderByCreatedAtDesc(normalizeUid(firebaseUid));
            if (memories.isEmpty()) {
                return "";
            }

            return memories.stream()
                    .map(m -> String.format("[%s | importancia %d]: %s",
                            m.getCategory(),
                            m.getImportanceWeight(),
                            m.getContent()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Recuperacao de memoria indisponivel: {}", e.getMessage());
            return "";
        }
    }

    @Transactional
    public void forget(String firebaseUid, String keyword) {
        try {
            ghostMemoryRepository.deleteMemoriesByKeyword(normalizeUid(firebaseUid), keyword);
            log.warn("Memorias contendo '{}' foram removidas para usuario {}", keyword, firebaseUid);
        } catch (Exception e) {
            log.warn("Falha no esquecimento seletivo: {}", e.getMessage());
        }
    }

    private String normalizeUid(String firebaseUid) {
        return firebaseUid == null || firebaseUid.isBlank() ? "Walker" : firebaseUid;
    }
}
