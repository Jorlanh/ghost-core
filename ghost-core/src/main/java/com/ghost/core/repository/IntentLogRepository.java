package com.ghost.core.repository;

import com.ghost.core.model.IntentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntentLogRepository extends JpaRepository<IntentLog, UUID> {

    @Query(value = """
        SELECT * FROM intent_logs
        WHERE firebase_uid = :firebaseUid
        ORDER BY created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<IntentLog> findRecentIntentLogs(
            @Param("firebaseUid") String firebaseUid,
            @Param("limit") int limit
    );
}