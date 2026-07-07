package com.ghost.core.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "ghost_memories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhostMemory {
    @Id
    private String id;

    @Field("firebase_uid")
    private String firebaseUid;

    @Field("content")
    private String content;

    // MongoDB armazena arrays nativamente
    @Field("embedding")
    private float[] embedding;

    @Builder.Default
    @Field("importance_weight")
    private Integer importanceWeight = 1;

    @Field("category")
    private String category;

    @Builder.Default
    @Field("metadata")
    private String metadata = "{}";

    @Builder.Default
    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}