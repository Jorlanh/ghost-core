package com.ghost.core.repository;

import com.ghost.core.model.GhostMemory;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GhostMemoryRepository extends MongoRepository<GhostMemory, String> {

    List<GhostMemory> findTop5ByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);

    // DELEÇÃO POR PALAVRA-CHAVE (Regex case-insensitive)
    @DeleteQuery("{ 'firebaseUid': ?0, 'content': { $regex: ?1, $options: 'i' } }")
    void deleteMemoriesByKeyword(String firebaseUid, String keyword);

    /* * NOTA SOBRE BUSCA VETORIAL:
     * O SQL com pgvector (<=>) não funciona aqui. 
     * Para busca vetorial no MongoDB, recomenda-se usar a Aggregation Framework 
     * ou Atlas Vector Search. 
     * * Se você estiver usando Atlas Vector Search, as consultas devem ser feitas
     * via @Aggregation pipeline ou MongoTemplate.
     */
}