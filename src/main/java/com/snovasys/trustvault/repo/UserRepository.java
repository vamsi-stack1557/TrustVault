package com.snovasys.trustvault.repo;

import com.snovasys.trustvault.model.AppUser;
import com.snovasys.trustvault.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRoleAndEnabledTrue(Role role);
    List<AppUser> findAllByOrderByCreatedAtDesc();
}
