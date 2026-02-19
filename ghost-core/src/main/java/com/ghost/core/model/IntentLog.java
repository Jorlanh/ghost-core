package com.ghost.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "intent_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @Column(name = "command_text", nullable = false)
    private String commandText;

    @Column(name = "ai_response")
    private String aiResponse;

    private String intentCategory;

    private Integer latencyMs;

    private Integer tokensUsed;

    @Builder.Default
    private Boolean success = true;

    private String errorMessage;

    @Builder.Default
    private Boolean memoryStored = false;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}