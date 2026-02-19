package com.ghost.core.repository;

import com.ghost.core.model.GhostMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GhostMemoryRepository extends JpaRepository<GhostMemory, UUID> {

    @Query(value = """
        SELECT * FROM ghost_memories
        WHERE firebase_uid = :firebaseUid
          AND (1 - (embedding <=> :queryVector::vector)) > :minSimilarity
        ORDER BY (1 - (embedding <=> :queryVector::vector)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<GhostMemory> findRelevantMemories(
            @Param("firebaseUid") String firebaseUid,
            @Param("queryVector") float[] queryVector,
            @Param("minSimilarity") double minSimilarity,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM ghost_memories
        WHERE firebase_uid = :firebaseUid
        ORDER BY 
            (1 - (embedding <=> :queryVector::vector)) * 0.7 +
            (importance_weight / 10.0) * 0.2 +
            (EXTRACT(EPOCH FROM created_at) / EXTRACT(EPOCH FROM NOW())) * 0.1 DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<GhostMemory> findHybridRelevantMemories(
            @Param("firebaseUid") String firebaseUid,
            @Param("queryVector") float[] queryVector,
            @Param("limit") int limit
    );

    @Modifying
    @Query(value = """
        DELETE FROM ghost_memories 
        WHERE firebase_uid = :firebaseUid 
          AND content ILIKE '%' || :keyword || '%'
        """, nativeQuery = true)
    void deleteMemoriesByKeyword(@Param("firebaseUid") String firebaseUid, @Param("keyword") String keyword);
}