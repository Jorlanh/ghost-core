package com.ghost.core.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "intent_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentLog {
    @Id
    private String id;

    @Field("firebase_uid")
    private String firebaseUid;

    @Field("command_text")
    private String commandText;

    @Field("ai_response")
    private String aiResponse;

    @Field("intent_category")
    private String intentCategory;

    @Field("latency_ms")
    private Integer latencyMs;

    @Field("tokens_used")
    private Integer tokensUsed;

    @Builder.Default
    @Field("success")
    private Boolean success = true;

    @Field("error_message")
    private String errorMessage;

    @Builder.Default
    @Field("memory_stored")
    private Boolean memoryStored = false;

    @Builder.Default
    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}