package com.ghost.core.repository;

import com.ghost.core.model.ApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiConfigRepository extends JpaRepository<ApiConfig, UUID> {
    // O Spring gera o SQL automaticamente baseado no nome deste método
    Optional<ApiConfig> findByServiceName(String serviceName);
}