package com.snovasys.trustvault.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vault_documents")
public class VaultDocument {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(name = "original_filename", nullable = false, length = 180)
    private String originalFilename;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(length = 1000)
    private String description;
    @Column(name = "storage_key", nullable = false, unique = true, length = 80)
    private String storageKey;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected VaultDocument() {}
    public VaultDocument(UUID id, AppUser owner, String title, String originalFilename, String category,
                         String description, String storageKey, String contentType, long sizeBytes, Instant now) {
        this.id = id; this.owner = owner; this.title = title; this.originalFilename = originalFilename;
        this.category = category; this.description = description; this.storageKey = storageKey;
        this.contentType = contentType; this.sizeBytes = sizeBytes; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void updateMetadata(String title, String category, String description, Instant now) {
        this.title = title; this.category = category; this.description = description; this.updatedAt = now;
    }
    public void replaceContent(String filename, String storageKey, String contentType, long sizeBytes, Instant now) {
        this.originalFilename = filename; this.storageKey = storageKey; this.contentType = contentType;
        this.sizeBytes = sizeBytes; this.updatedAt = now;
    }
    public void softDelete(Instant now) { this.deletedAt = now; this.updatedAt = now; }
}
