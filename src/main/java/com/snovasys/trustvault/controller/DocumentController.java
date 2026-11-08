package com.snovasys.trustvault.controller;

import com.snovasys.trustvault.dto.ApiDtos.*;
import com.snovasys.trustvault.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;
    public DocumentController(DocumentService documents) { this.documents=documents; }

    @GetMapping
    public List<DocumentResponse> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String owner,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return documents.list(q, owner, category, from, to);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file,
                                    @RequestParam String title,
                                    @RequestParam String category,
                                    @RequestParam(required = false) String description) {
        return documents.upload(file, title, category, description);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) { return documents.get(id); }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID id) {
        DocumentService.Download file = documents.download(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentLength(file.sizeBytes());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");
        headers.setPragma("no-cache");
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(file.content()));
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return documents.updateMetadata(id, request);
    }

    @PutMapping(value = "/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse replaceContent(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return documents.replaceContent(id, file);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable UUID id) {
        documents.delete(id);
        return new MessageResponse("Document moved to deleted state");
    }

    @GetMapping("/{id}/permissions")
    public List<GrantResponse> permissions(@PathVariable UUID id) { return documents.permissions(id); }

    @PostMapping("/{id}/permissions")
    public GrantResponse grant(@PathVariable UUID id, @Valid @RequestBody GrantRequest request) {
        return documents.grant(id, request);
    }

    @DeleteMapping("/{id}/permissions/{grantId}")
    public MessageResponse revoke(@PathVariable UUID id, @PathVariable UUID grantId) {
        documents.revoke(id, grantId);
        return new MessageResponse("Access revoked immediately for future requests");
    }
}
