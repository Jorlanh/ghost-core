package com.ghost.core.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "users_context")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsersContext {
    @Id
    private String id;

    @Field("firebase_uid")
    private String firebaseUid;

    @Field("nickname")
    private String nickname;

    @Field("god_mode")
    @Builder.Default
    private Boolean godMode = false;

    @Field("last_interaction")
    private LocalDateTime lastInteraction;

    @Builder.Default
    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}