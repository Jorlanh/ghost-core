package com.ghost.core.repository;

import com.ghost.core.model.IntentLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IntentLogRepository extends MongoRepository<IntentLog, String> {

    // O Spring Data gera a consulta automaticamente
    // Você deve passar PageRequest.of(0, limit) no seu Service
    List<IntentLog> findByFirebaseUidOrderByCreatedAtDesc(String firebaseUid, Pageable pageable);
}