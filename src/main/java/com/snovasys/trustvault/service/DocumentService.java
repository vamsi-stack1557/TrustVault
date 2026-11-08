package com.snovasys.trustvault.service;

import com.snovasys.trustvault.dto.ApiDtos.*;
import com.snovasys.trustvault.model.*;
import com.snovasys.trustvault.repo.DocumentGrantRepository;
import com.snovasys.trustvault.repo.DocumentRepository;
import com.snovasys.trustvault.repo.UserRepository;
import com.snovasys.trustvault.storage.PrivateFileStorage;
import com.snovasys.trustvault.storage.StoredFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.snovasys.trustvault.service.VaultExceptions.*;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final DocumentGrantRepository grants;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final PrivateFileStorage storage;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, DocumentGrantRepository grants, UserRepository users,
                           CurrentUserService currentUser, PrivateFileStorage storage, AuditService audit) {
        this.documents=documents; this.grants=grants; this.users=users; this.currentUser=currentUser;
        this.storage=storage; this.audit=audit;
    }

    @Transactional
    public List<DocumentResponse> list(String query, String owner, String category, LocalDate from, LocalDate to) {
        AppUser actor = currentUser.requireUser();
        if (actor.getRole() == Role.ADMIN) { audit.denied(actor, "DOCUMENT_LIST", "DOCUMENT", null, "Administrator role has no implicit document access"); throw new Forbidden(); }
        if (from != null && to != null && to.isBefore(from)) throw new BadRequest("End date must not be before start date");
        Instant fromDate = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toDate = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<DocumentResponse> result = documents.findAccessible(actor.getId(), blankToNull(query), blankToNull(owner), blankToNull(category),
                fromDate, toDate, PageRequest.of(0, 200)).stream().map(d -> toResponse(d, actor)).toList();
        audit.success(actor, "DOCUMENT_LIST", "DOCUMENT", null, "Returned " + result.size() + " accessible document summaries");
        return result;
    }

    @Transactional
    public DocumentResponse get(UUID id) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = readableDocument(actor, id, "DOCUMENT_READ");
        audit.success(actor, "DOCUMENT_METADATA_READ", "DOCUMENT", id.toString(), "Document metadata viewed");
        return toResponse(doc, actor);
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file, String title, String category, String description) {
        AppUser actor = currentUser.requireUser();
        if (actor.getRole() != Role.OWNER) { audit.denied(actor, "DOCUMENT_UPLOAD", "DOCUMENT", null, "Only document owners can upload"); throw new Forbidden(); }
        title = requiredText(title, 180, "Title");
        category = requiredText(category, 80, "Category");
        description = cleanOptional(description, 1000);
        StoredFile stored = storage.store(file);
        try {
            Instant now = Instant.now();
            VaultDocument doc = new VaultDocument(UUID.randomUUID(), actor, title, stored.originalFilename(), category,
                    description, stored.storageKey(), stored.contentType(), stored.sizeBytes(), now);
            documents.save(doc);
            audit.success(actor, "DOCUMENT_UPLOAD", "DOCUMENT", doc.getId().toString(),
                    "Uploaded " + stored.originalFilename() + " (" + stored.sizeBytes() + " bytes)");
            return toResponse(doc, actor);
        } catch (RuntimeException ex) {
            storage.deleteUncommitted(stored.storageKey());
            throw ex;
        }
    }

    @Transactional
    public DocumentResponse updateMetadata(UUID id, UpdateDocumentRequest request) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = editableDocument(actor, id, "DOCUMENT_EDIT");
        doc.updateMetadata(requiredText(request.title(), 180, "Title"), requiredText(request.category(), 80, "Category"),
                cleanOptional(request.description(), 1000), Instant.now());
        audit.success(actor, "DOCUMENT_METADATA_UPDATED", "DOCUMENT", id.toString(), "Updated title/category/description");
        return toResponse(doc, actor);
    }

    @Transactional
    public DocumentResponse replaceContent(UUID id, MultipartFile file) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = editableDocument(actor, id, "DOCUMENT_CONTENT_EDIT");
        StoredFile stored = storage.store(file);
        try {
            doc.replaceContent(stored.originalFilename(), stored.storageKey(), stored.contentType(), stored.sizeBytes(), Instant.now());
            audit.success(actor, "DOCUMENT_CONTENT_REPLACED", "DOCUMENT", id.toString(),
                    "New content " + stored.originalFilename() + " (" + stored.sizeBytes() + " bytes); old blob retained");
            return toResponse(doc, actor);
        } catch (RuntimeException ex) {
            storage.deleteUncommitted(stored.storageKey());
            throw ex;
        }
    }

    @Transactional
    public Download download(UUID id) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = readableDocument(actor, id, "DOCUMENT_DOWNLOAD");
        byte[] content = storage.readDecrypted(doc.getStorageKey());
        audit.success(actor, "DOCUMENT_DOWNLOAD", "DOCUMENT", id.toString(), "Download authorised; filename=" + doc.getOriginalFilename());
        return new Download(content, doc.getOriginalFilename(), doc.getContentType(), doc.getSizeBytes());
    }

    @Transactional
    public void delete(UUID id) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = documents.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (doc == null || !doc.getOwner().getId().equals(actor.getId())) {
            audit.denied(actor, "DOCUMENT_DELETE", "DOCUMENT", id.toString(), "Delete requires document ownership");
            throw new NotFound();
        }
        doc.softDelete(Instant.now());
        audit.success(actor, "DOCUMENT_DELETE", "DOCUMENT", id.toString(), "Document soft-deleted; private blob retained for recovery/retention");
    }

    @Transactional
    public List<GrantResponse> permissions(UUID id) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = ownedDocument(actor, id, "PERMISSIONS_READ");
        audit.success(actor, "PERMISSIONS_READ", "DOCUMENT", id.toString(), "Document permissions inspected");
        return grants.findAllByDocumentIdOrderByCreatedAtDesc(doc.getId()).stream()
                .map(g -> new GrantResponse(g.getId(), g.getUser().getEmail(), g.getUser().getDisplayName(), g.getPermission(), g.getCreatedAt())).toList();
    }

    @Transactional
    public GrantResponse grant(UUID id, GrantRequest request) {
        AppUser actor = currentUser.requireUser();
        VaultDocument doc = ownedDocument(actor, id, "PERMISSION_GRANT");
        AppUser target = users.findByEmailIgnoreCase(request.email()).orElse(null);
        if (target == null || !target.isEnabled() || target.getRole() == Role.ADMIN || target.getId().equals(actor.getId())) {
            audit.denied(actor, "PERMISSION_GRANT", "DOCUMENT", id.toString(), "Grant target is missing, disabled, an admin, or the owner");
            throw new BadRequest("Choose an active non-administrator account other than yourself");
        }
        DocumentGrant grant = grants.findByDocumentIdAndUserId(id, target.getId()).orElse(null);
        String action;
        if (grant == null) {
            grant = new DocumentGrant(UUID.randomUUID(), doc, target, actor, request.permission(), Instant.now());
            action = "PERMISSION_GRANTED";
        } else {
            grant.setPermission(request.permission());
            action = "PERMISSION_UPDATED";
        }
        grants.save(grant);
        audit.success(actor, action, "DOCUMENT", id.toString(), target.getEmail() + " => " + request.permission());
        return new GrantResponse(grant.getId(), target.getEmail(), target.getDisplayName(), grant.getPermission(), grant.getCreatedAt());
    }

    @Transactional
    public void revoke(UUID id, UUID grantId) {
        AppUser actor = currentUser.requireUser();
        ownedDocument(actor, id, "PERMISSION_REVOKE");
        DocumentGrant grant = grants.findByIdAndDocumentId(grantId, id).orElse(null);
        if (grant == null) {
            audit.denied(actor, "PERMISSION_REVOKE", "DOCUMENT", id.toString(), "Grant was not found for this document");
            throw new NotFound();
        }
        String target = grant.getUser().getEmail();
        grants.delete(grant);
        audit.success(actor, "PERMISSION_REVOKED", "DOCUMENT", id.toString(), "Revoked " + target + " grant " + grantId);
    }

    private VaultDocument readableDocument(AppUser actor, UUID id, String action) {
        VaultDocument doc = documents.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (doc == null || !canRead(doc, actor)) {
            audit.denied(actor, action, "DOCUMENT", id.toString(), "Document absent or caller has no active read grant");
            throw new NotFound();
        }
        return doc;
    }
    private VaultDocument editableDocument(AppUser actor, UUID id, String action) {
        VaultDocument doc = documents.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (doc == null || !canEdit(doc, actor)) {
            audit.denied(actor, action, "DOCUMENT", id.toString(), "Edit requires owner status or an active EDIT grant");
            throw new NotFound();
        }
        return doc;
    }
    private VaultDocument ownedDocument(AppUser actor, UUID id, String action) {
        VaultDocument doc = documents.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (doc == null || !doc.getOwner().getId().equals(actor.getId())) {
            audit.denied(actor, action, "DOCUMENT", id.toString(), "Permission management requires ownership");
            throw new NotFound();
        }
        return doc;
    }
    private boolean canRead(VaultDocument doc, AppUser user) {
        if (doc.getOwner().getId().equals(user.getId())) return true;
        if (user.getRole() == Role.ADMIN) return false;
        return grants.findByDocumentIdAndUserId(doc.getId(), user.getId()).isPresent();
    }
    private boolean canEdit(VaultDocument doc, AppUser user) {
        if (doc.getOwner().getId().equals(user.getId())) return true;
        if (user.getRole() == Role.ADMIN) return false;
        return grants.findByDocumentIdAndUserId(doc.getId(), user.getId()).map(g -> g.getPermission() == Permission.EDIT).orElse(false);
    }
    private DocumentResponse toResponse(VaultDocument d, AppUser user) {
        boolean owner = d.getOwner().getId().equals(user.getId());
        Permission permission = owner ? null : grants.findByDocumentIdAndUserId(d.getId(), user.getId()).map(DocumentGrant::getPermission).orElse(null);
        return new DocumentResponse(d.getId(), d.getTitle(), d.getOriginalFilename(), d.getCategory(), d.getDescription(),
                d.getOwner().getEmail(), d.getCreatedAt(), d.getUpdatedAt(), d.getSizeBytes(), d.getContentType(),
                owner ? "OWNER" : permission == null ? "NONE" : permission.name(), owner,
                owner || permission == Permission.EDIT, owner);
    }
    private String requiredText(String value, int max, String field) {
        if (value == null || value.isBlank()) throw new BadRequest(field + " is required");
        String text = value.trim();
        if (text.length() > max) throw new BadRequest(field + " is too long");
        return text;
    }
    private String cleanOptional(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() > max) throw new BadRequest("Description is too long");
        return text.isEmpty() ? null : text;
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record Download(byte[] content, String filename, String contentType, long sizeBytes) {}
}
