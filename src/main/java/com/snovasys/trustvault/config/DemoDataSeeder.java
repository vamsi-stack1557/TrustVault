package com.snovasys.trustvault.config;

import com.snovasys.trustvault.model.*;
import com.snovasys.trustvault.repo.DocumentGrantRepository;
import com.snovasys.trustvault.repo.DocumentRepository;
import com.snovasys.trustvault.repo.UserRepository;
import com.snovasys.trustvault.service.AuditService;
import com.snovasys.trustvault.storage.PrivateFileStorage;
import com.snovasys.trustvault.storage.StoredFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class DemoDataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final DocumentRepository documents;
    private final DocumentGrantRepository grants;
    private final PasswordEncoder passwordEncoder;
    private final PrivateFileStorage storage;
    private final AuditService audit;
    private final boolean enabled;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public DemoDataSeeder(UserRepository users, DocumentRepository documents, DocumentGrantRepository grants,
                          PasswordEncoder passwordEncoder, PrivateFileStorage storage, AuditService audit,
                          @Value("${app.seed-demo-data:true}") boolean enabled,
                          @Value("${app.bootstrap-admin-email:}") String bootstrapEmail,
                          @Value("${app.bootstrap-admin-password:}") String bootstrapPassword) {
        this.users=users; this.documents=documents; this.grants=grants; this.passwordEncoder=passwordEncoder;
        this.storage=storage; this.audit=audit; this.enabled=enabled; this.bootstrapEmail=bootstrapEmail; this.bootstrapPassword=bootstrapPassword;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            if (users.count() == 0) {
                if (bootstrapEmail == null || bootstrapEmail.isBlank() || bootstrapPassword == null || bootstrapPassword.length() < 16) {
                    throw new IllegalStateException("Empty database: set TRUSTVAULT_BOOTSTRAP_ADMIN_EMAIL and a 16+ character TRUSTVAULT_BOOTSTRAP_ADMIN_PASSWORD for first startup");
                }
                users.save(new AppUser(bootstrapEmail.trim(), "TrustVault Administrator", passwordEncoder.encode(bootstrapPassword), Role.ADMIN, true, Instant.now()));
            }
            return;
        }
        AppUser admin = users.findByEmailIgnoreCase("admin@trustvault.local").orElseGet(() -> users.save(
                new AppUser("admin@trustvault.local", "TrustVault Admin", passwordEncoder.encode("Admin@12345"), Role.ADMIN, true, Instant.now())));
        AppUser owner = users.findByEmailIgnoreCase("owner@trustvault.local").orElseGet(() -> users.save(
                new AppUser("owner@trustvault.local", "Document Owner", passwordEncoder.encode("Owner@12345"), Role.OWNER, true, Instant.now())));
        AppUser viewer = users.findByEmailIgnoreCase("viewer@trustvault.local").orElseGet(() -> users.save(
                new AppUser("viewer@trustvault.local", "Shared Viewer", passwordEncoder.encode("Viewer@12345"), Role.VIEWER, true, Instant.now())));

        if (documents.count() == 0) {
            byte[] sample = ("TrustVault demo document\n\nThis file is stored outside the public web directory. " +
                    "The viewer account has a READ grant to this sample. Try signing in as the viewer, then revoke the grant as the owner.\n")
                    .getBytes(StandardCharsets.UTF_8);
            StoredFile file = storage.storeSeedText("welcome.txt", sample);
            VaultDocument doc = documents.save(new VaultDocument(UUID.randomUUID(), owner, "Welcome to TrustVault",
                    file.originalFilename(), "Getting Started", "A safe example document shared with the viewer account.",
                    file.storageKey(), file.contentType(), file.sizeBytes(), Instant.now()));
            grants.save(new DocumentGrant(UUID.randomUUID(), doc, viewer, owner, Permission.READ, Instant.now()));
            audit.success(owner, "DEMO_DOCUMENT_SEEDED", "DOCUMENT", doc.getId().toString(), "Seeded sample doc with READ grant for viewer account");
        }
    }
}
