package com.snovasys.trustvault.repo;

import com.snovasys.trustvault.model.AuditChainState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface AuditChainStateRepository extends JpaRepository<AuditChainState, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuditChainState s where s.id = 1")
    Optional<AuditChainState> lockSingleton();
}
