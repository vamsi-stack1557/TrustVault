package com.snovasys.trustvault.controller;

import com.snovasys.trustvault.dto.ApiDtos.*;
import com.snovasys.trustvault.model.AppUser;
import com.snovasys.trustvault.repo.UserRepository;
import com.snovasys.trustvault.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final UserRepository users;
    private final AuditService audit;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          UserRepository users, AuditService audit) {
        this.authenticationManager=authenticationManager; this.contextRepository=contextRepository; this.users=users; this.audit=audit;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim(), body.password()));
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            AppUser user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
            audit.success(user, "LOGIN_SUCCESS", "SESSION", session.getId(), "Session established");
            return ResponseEntity.ok(new LoginResponse(user.getEmail(), user.getDisplayName(), user.getRole()));
        } catch (AuthenticationException ex) {
            audit.failure(body.email(), "LOGIN_FAILED", "SESSION", null, "Invalid credentials or disabled account");
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid email or password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUser user = auth == null ? null : users.findByEmailIgnoreCase(auth.getName()).orElse(null);
        audit.success(user, "LOGOUT", "SESSION", null, "Session invalidated");
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(new MessageResponse("Signed out"));
    }
}
