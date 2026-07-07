package com.ghost.core.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "api_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiConfig {
    @Id
    private String id; // MongoDB usa String ou ObjectId

    @Field("service_name")
    private String serviceName;

    @Field("api_key")
    private String apiKey;

    @Field("base_url")
    private String baseUrl;

    @Field("priority_level")
    private Integer priorityLevel;

    @Builder.Default
    @Field("is_active")
    private Boolean isActive = true;

    @Field("updated_at")
    private LocalDateTime updatedAt;
}