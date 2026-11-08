package com.snovasys.trustvault.repo;

import com.snovasys.trustvault.model.VaultDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<VaultDocument, UUID> {
    Optional<VaultDocument> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
        select distinct d from VaultDocument d
        left join DocumentGrant g on g.document = d and g.user.id = :userId
        where d.deletedAt is null
          and (d.owner.id = :userId or g.user.id = :userId)
          and (:q is null or lower(d.title) like lower(concat('%', :q, '%'))
               or lower(d.originalFilename) like lower(concat('%', :q, '%')))
          and (:owner is null or lower(d.owner.email) like lower(concat('%', :owner, '%')))
          and (:category is null or lower(d.category) = lower(:category))
          and (:fromDate is null or d.createdAt >= :fromDate)
          and (:toDate is null or d.createdAt < :toDate)
        order by d.updatedAt desc
        """)
    List<VaultDocument> findAccessible(@Param("userId") Long userId,
                                       @Param("q") String q,
                                       @Param("owner") String owner,
                                       @Param("category") String category,
                                       @Param("fromDate") Instant fromDate,
                                       @Param("toDate") Instant toDate,
                                       Pageable pageable);

    long countByDeletedAtIsNull();
}
