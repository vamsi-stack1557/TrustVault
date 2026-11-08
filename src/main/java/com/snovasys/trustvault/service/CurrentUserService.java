package com.snovasys.trustvault.service;

import com.snovasys.trustvault.model.AppUser;
import com.snovasys.trustvault.model.Role;
import com.snovasys.trustvault.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import static com.snovasys.trustvault.service.VaultExceptions.Forbidden;

@Service
public class CurrentUserService {
    private final UserRepository users;
    private final AuditService audit;
    public CurrentUserService(UserRepository users, AuditService audit) { this.users = users; this.audit = audit; }
    public AppUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) throw new Forbidden();
        AppUser user = users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (user == null) {
            audit.failure(authentication.getName(), "AUTH_ACCOUNT_MISSING", "USER", null, "Authenticated session has no matching account");
            throw new Forbidden();
        }
        if (!user.isEnabled()) {
            audit.denied(user, "DISABLED_ACCOUNT_ACCESS", "USER", user.getId().toString(), "Disabled account attempted to use an existing session");
            throw new Forbidden();
        }
        return user;
    }
    public AppUser requireRole(Role role) {
        AppUser user = requireUser();
        if (user.getRole() != role) throw new Forbidden();
        return user;
    }
}
