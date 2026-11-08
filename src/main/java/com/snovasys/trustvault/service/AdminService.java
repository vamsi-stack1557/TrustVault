package com.snovasys.trustvault.service;

import com.snovasys.trustvault.dto.ApiDtos.*;
import com.snovasys.trustvault.model.AppUser;
import com.snovasys.trustvault.model.Role;
import com.snovasys.trustvault.repo.AuditEventRepository;
import com.snovasys.trustvault.repo.DocumentGrantRepository;
import com.snovasys.trustvault.repo.DocumentRepository;
import com.snovasys.trustvault.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static com.snovasys.trustvault.service.VaultExceptions.*;

@Service
public class AdminService {
    private final CurrentUserService currentUser;
    private final UserRepository users;
    private final DocumentRepository documents;
    private final DocumentGrantRepository grants;
    private final AuditEventRepository events;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminService(CurrentUserService currentUser, UserRepository users, DocumentRepository documents,
                        DocumentGrantRepository grants, AuditEventRepository events,
                        PasswordEncoder passwordEncoder, AuditService audit) {
        this.currentUser=currentUser; this.users=users; this.documents=documents; this.grants=grants;
        this.events=events; this.passwordEncoder=passwordEncoder; this.audit=audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        requireAdmin();
        return users.findAllByOrderByCreatedAtDesc().stream().map(this::toUserResponse).toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        AppUser actor = requireAdmin();
        if (request.role() == Role.ADMIN) throw new BadRequest("Administrator accounts cannot be created from this endpoint");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new Conflict("An account with that email already exists");
        AppUser created = users.save(new AppUser(email, request.displayName().trim(), passwordEncoder.encode(request.password()),
                request.role(), true, Instant.now()));
        audit.success(actor, "USER_CREATED", "USER", created.getId().toString(), email + " role=" + request.role());
        return toUserResponse(created);
    }

    @Transactional
    public UserResponse setEnabled(long id, boolean enabled) {
        AppUser actor = requireAdmin();
        AppUser target = users.findById(id).orElseThrow(NotFound::new);
        if (target.getId().equals(actor.getId()) && !enabled) throw new BadRequest("You cannot disable your own active administrator account");
        if (target.getRole() == Role.ADMIN && target.isEnabled() && !enabled && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BadRequest("The last active administrator cannot be disabled");
        }
        target.setEnabled(enabled);
        audit.success(actor, enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", Long.toString(id), target.getEmail());
        return toUserResponse(target);
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        requireAdmin();
        return new OverviewResponse(users.count(), documents.countByDeletedAtIsNull(), grants.count(), events.count());
    }

    public List<AuditResponse> auditEvents(int limit) {
        AppUser actor = requireAdmin();
        List<AuditResponse> result = audit.latest(limit);
        audit.success(actor, "AUDIT_LOG_READ", "AUDIT", null, "Administrator read recent audit events");
        return result;
    }

    public AuditVerifyResponse verifyAudit() {
        AppUser actor = requireAdmin();
        AuditVerifyResponse result = audit.verify();
        audit.success(actor, "AUDIT_CHAIN_VERIFIED", "AUDIT", null,
                "valid=" + result.valid() + "; checked=" + result.checkedEvents());
        return result;
    }

    private AppUser requireAdmin() {
        AppUser actor = currentUser.requireUser();
        if (actor.getRole() != Role.ADMIN) {
            audit.denied(actor, "ADMIN_ACCESS", "ADMIN", null, "Administrator role required");
            throw new Forbidden();
        }
        return actor;
    }
    private UserResponse toUserResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
