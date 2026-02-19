package com.ghost.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "service_name", unique = true, nullable = false)
    private String serviceName;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "priority_level")
    private Integer priorityLevel;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}