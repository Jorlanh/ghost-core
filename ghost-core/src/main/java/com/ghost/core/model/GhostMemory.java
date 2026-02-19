package com.ghost.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ghost_memories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhostMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firebase_uid", referencedColumnName = "firebase_uid", insertable = false, updatable = false)
    private UsersContext user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Embedding vetorial (1536 dims - compatível com Gemini 1.5, text-embedding-ada-002, etc.)
     * Mapeamento nativo via hibernate-vector + pgvector
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)", nullable = true)
    private float[] embedding;

    @Builder.Default
    @Column(name = "importance_weight")
    private Integer importanceWeight = 1;

    @Column(name = "category")
    private String category;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String metadata = "{}";

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}