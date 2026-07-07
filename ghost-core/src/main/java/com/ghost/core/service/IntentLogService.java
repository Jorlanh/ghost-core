package com.ghost.core.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class IntentLogService {
    
    private final IntentLogRepository repository;

    public IntentLogService(IntentLogRepository repository) {
        this.repository = repository;
    }

    public List<IntentLog> getRecentLogs(String firebaseUid, int limit) {
        // O PageRequest.of(0, limit) substitui o LIMIT do SQL
        return repository.findByFirebaseUidOrderByCreatedAtDesc(
            firebaseUid, 
            PageRequest.of(0, limit)
        );
    }
}