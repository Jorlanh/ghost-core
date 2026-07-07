package com.ghost.core.repository;

import com.ghost.core.model.ApiConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ApiConfigRepository extends MongoRepository<ApiConfig, String> {
    Optional<ApiConfig> findByServiceName(String serviceName);
}