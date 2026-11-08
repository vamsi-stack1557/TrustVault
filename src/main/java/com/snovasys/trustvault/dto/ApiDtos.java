package com.snovasys.trustvault.dto;

import com.snovasys.trustvault.model.Outcome;
import com.snovasys.trustvault.model.Permission;
import com.snovasys.trustvault.model.Role;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record LoginResponse(String email, String displayName, Role role) {}
    public record CsrfResponse(String headerName, String token) {}
    public record DocumentResponse(UUID id, String title, String originalFilename, String category,
                                   String description, String ownerEmail, Instant createdAt, Instant updatedAt,
                                   long sizeBytes, String contentType, String permission,
                                   boolean isOwner, boolean canEdit, boolean canManage) {}
    public record UpdateDocumentRequest(@NotBlank @Size(max = 180) String title,
                                       @NotBlank @Size(max = 80) String category,
                                       @Size(max = 1000) String description) {}
    public record GrantRequest(@Email @NotBlank String email, @NotNull Permission permission) {}
    public record GrantResponse(UUID id, String email, String displayName, Permission permission, Instant createdAt) {}
    public record CreateUserRequest(@Email @NotBlank String email,
                                    @NotBlank @Size(max = 120) String displayName,
                                    @NotBlank @Size(min = 12, max = 72) String password,
                                    @NotNull Role role) {}
    public record StatusRequest(@NotNull Boolean enabled) {}
    public record UserResponse(Long id, String email, String displayName, Role role, boolean enabled, Instant createdAt) {}
    public record AuditResponse(Long id, Instant createdAt, Long actorId, String actorEmail, String action,
                                String resourceType, String resourceId, Outcome outcome, String details,
                                String ipAddress, String previousHash, String eventHash) {}
    public record AuditVerifyResponse(boolean valid, long checkedEvents, String expectedLastHash,
                                      String storedLastHash, String message) {}
    public record OverviewResponse(long users, long documents, long grants, long auditEvents) {}
    public record MessageResponse(String message) {}
}
