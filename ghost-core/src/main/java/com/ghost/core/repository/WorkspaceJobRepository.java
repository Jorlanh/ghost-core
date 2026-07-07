package com.ghost.core.repository;

import com.ghost.core.model.WorkspaceJobDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceJobRepository extends MongoRepository<WorkspaceJobDocument, String> {
}