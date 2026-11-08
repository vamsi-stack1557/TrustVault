package com.snovasys.trustvault.repo;

import com.snovasys.trustvault.model.DocumentGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentGrantRepository extends JpaRepository<DocumentGrant, UUID> {
    Optional<DocumentGrant> findByDocumentIdAndUserId(UUID documentId, Long userId);
    List<DocumentGrant> findAllByDocumentIdOrderByCreatedAtDesc(UUID documentId);
    Optional<DocumentGrant> findByIdAndDocumentId(UUID id, UUID documentId);
    long count();
}
