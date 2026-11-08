package com.snovasys.trustvault.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_grants", uniqueConstraints = @UniqueConstraint(name = "uq_document_grant", columnNames = {"document_id", "user_id"}))
public class DocumentGrant {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id", nullable = false)
    private VaultDocument document;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "granted_by", nullable = false)
    private AppUser grantedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Permission permission;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected DocumentGrant() {}
    public DocumentGrant(UUID id, VaultDocument document, AppUser user, AppUser grantedBy, Permission permission, Instant createdAt) {
        this.id=id; this.document=document; this.user=user; this.grantedBy=grantedBy; this.permission=permission; this.createdAt=createdAt;
    }
    public UUID getId() { return id; }
    public VaultDocument getDocument() { return document; }
    public AppUser getUser() { return user; }
    public AppUser getGrantedBy() { return grantedBy; }
    public Permission getPermission() { return permission; }
    public Instant getCreatedAt() { return createdAt; }
    public void setPermission(Permission permission) { this.permission = permission; }
}
