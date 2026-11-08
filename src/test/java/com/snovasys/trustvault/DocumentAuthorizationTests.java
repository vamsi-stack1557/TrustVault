package com.snovasys.trustvault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.trustvault.model.*;
import com.snovasys.trustvault.repo.*;
import com.snovasys.trustvault.storage.PrivateFileStorage;
import com.snovasys.trustvault.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trustvault-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.storage-dir=./target/test-private-files",
        "app.audit-hmac-key=TEST-ONLY-HMAC-KEY-THAT-IS-AT-LEAST-32-BYTES-LONG",
        "app.seed-demo-data=true"
})
class DocumentAuthorizationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DocumentRepository documents;
    @Autowired DocumentGrantRepository grants;
    @Autowired AuditEventRepository events;
    @Autowired PrivateFileStorage storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private AppUser owner;
    private AppUser viewer;
    private VaultDocument privateDocument;

    @BeforeEach
    void setUp() {
        owner = users.findByEmailIgnoreCase("owner@trustvault.local").orElseThrow();
        viewer = users.findByEmailIgnoreCase("viewer@trustvault.local").orElseThrow();
        StoredFile blob = storage.storeSeedText("owner-private.txt", "owner only".getBytes());
        privateDocument = documents.saveAndFlush(new VaultDocument(UUID.randomUUID(), owner,
                "Owner private note", blob.originalFilename(), "Private", "Not shared with the viewer",
                blob.storageKey(), blob.contentType(), blob.sizeBytes(), Instant.now()));
    }

    @Test
    void viewerCannotReadDownloadEditOrDeleteAnUnsharedDocument() throws Exception {
        String id = privateDocument.getId().toString();
        var viewerAuth = SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER");

        mvc.perform(get("/api/documents/{id}", id).with(viewerAuth))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{id}/download", id).with(viewerAuth))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/documents/{id}", id).with(viewerAuth).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"stolen\",\"category\":\"Private\",\"description\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/{id}", id).with(viewerAuth).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{id}/permissions", id).with(viewerAuth))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/{id}/permissions", id).with(viewerAuth).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@trustvault.local\",\"permission\":\"READ\"}"))
                .andExpect(status().isNotFound());

        assertThat(events.findAllByOrderByIdAsc()).anySatisfy(event -> {
            assertThat(event.getActorEmail()).isEqualTo(viewer.getEmail());
            assertThat(event.getOutcome()).isEqualTo(Outcome.DENIED);
            assertThat(event.getResourceId()).isEqualTo(id);
        });
    }

    @Test
    void revokingGrantImmediatelyBlocksMetadataAndDownload() throws Exception {
        String id = privateDocument.getId().toString();
        String grantJson = "{\"email\":\"viewer@trustvault.local\",\"permission\":\"READ\"}";
        String response = mvc.perform(post("/api/documents/{id}/permissions", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(owner.getEmail()).roles("OWNER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(grantJson))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode grant = objectMapper.readTree(response);
        String grantId = grant.get("id").asText();

        mvc.perform(get("/api/documents/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/documents/{id}/permissions/{grantId}", id, grantId)
                        .with(SecurityMockMvcRequestPostProcessors.user(owner.getEmail()).roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/documents/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{id}/download", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void editGrantAllowsContentChangesButNeverSharingOrDeletion() throws Exception {
        String id = privateDocument.getId().toString();
        mvc.perform(post("/api/documents/{id}/permissions", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(owner.getEmail()).roles("OWNER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"viewer@trustvault.local\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isOk());

        String update = mvc.perform(put("/api/documents/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Edited by grantee\",\"category\":\"Private\",\"description\":\"EDIT grant works\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode updated = objectMapper.readTree(update);
        assertThat(updated.get("isOwner").asBoolean()).isFalse();
        assertThat(updated.get("canEdit").asBoolean()).isTrue();
        assertThat(updated.get("canManage").asBoolean()).isFalse();

        mvc.perform(delete("/api/documents/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{id}/permissions", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void administratorDoesNotHaveImplicitDocumentAccess() throws Exception {
        var adminAuth = SecurityMockMvcRequestPostProcessors.user("admin@trustvault.local").roles("ADMIN");
        mvc.perform(get("/api/documents/{id}", privateDocument.getId()).with(adminAuth))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents").with(adminAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotUploadOrInspectAdminAudit() throws Exception {
        var viewerAuth = SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER");
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/documents").file(file).param("title", "Viewer upload")
                        .param("category", "Test").with(viewerAuth).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").with(viewerAuth))
                .andExpect(status().isForbidden());
    }


    @Test
    void disabledAccountCannotReuseAnExistingAuthenticatedSession() throws Exception {
        viewer.setEnabled(false);
        users.saveAndFlush(viewer);
        try {
            mvc.perform(get("/api/documents")
                            .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")))
                    .andExpect(status().isForbidden());
            assertThat(events.findAllByOrderByIdAsc()).anySatisfy(event -> {
                assertThat(event.getActorEmail()).isEqualTo(viewer.getEmail());
                assertThat(event.getAction()).isEqualTo("DISABLED_ACCOUNT_ACCESS");
                assertThat(event.getOutcome()).isEqualTo(Outcome.DENIED);
            });
        } finally {
            viewer.setEnabled(true);
            users.saveAndFlush(viewer);
        }
    }

    @Test
    void tamperingWithAuditDetailsIsDetectedByHmacVerification() throws Exception {
        // Create at least one event through a denied document request.
        mvc.perform(get("/api/documents/{id}", privateDocument.getId())
                .with(SecurityMockMvcRequestPostProcessors.user(viewer.getEmail()).roles("VIEWER")))
                .andExpect(status().isNotFound());
        AuditEvent event = events.findAllByOrderByIdAsc().get(0);
        jdbc.update("update audit_events set details = ? where id = ?", "tampered after signing", event.getId());

        String body = mvc.perform(get("/api/admin/audit/verify")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin@trustvault.local").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("valid").asBoolean()).isFalse();
    }

    @Test
    void fileSignatureMustMatchAnAllowedExtension() throws Exception {
        MockMultipartFile fakePdf = new MockMultipartFile("file", "not-a-pdf.pdf", "application/pdf", "plain text".getBytes());
        mvc.perform(multipart("/api/documents").file(fakePdf).param("title", "Fake PDF")
                        .param("category", "Test")
                        .with(SecurityMockMvcRequestPostProcessors.user(owner.getEmail()).roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
    @Test
    void uploadedFileIsEncryptedAtRestAndOwnerReceivesPlaintextOnlyAfterAuthorisation() throws Exception {
        byte[] plaintext = "this is confidential example content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "contract.txt", "text/plain", plaintext);
        String response = mvc.perform(multipart("/api/documents").file(file).param("title", "Encrypted contract")
                        .param("category", "Contracts")
                        .with(SecurityMockMvcRequestPostProcessors.user(owner.getEmail()).roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        VaultDocument saved = documents.findById(id).orElseThrow();
        byte[] encrypted = java.nio.file.Files.readAllBytes(storage.resolve(saved.getStorageKey()));
        assertThat(new String(encrypted)).doesNotContain("confidential example content");

        mvc.perform(get("/api/documents/{id}/download", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(owner.getEmail()).roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string("this is confidential example content"));
    }

}
