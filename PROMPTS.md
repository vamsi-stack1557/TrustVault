# PROMPTS.md — AI-assisted build log

**Honest status:** The source archive was scaffolded in one working session on **2026-11-04**. The entries below document the real AI-assisted implementation/review tasks in that session; they are not a fabricated seven-day commit history. The project has not yet been pushed to a public Git repository. Therefore the commit URLs are deliberately marked **PENDING**. After you review and change the code, replace each pending marker with the actual public commit URL that contains the relevant correction. Continue adding entries with real dates and commits throughout the challenge week.

## 2026-11-04 — per-document authorisation and role boundaries

- **Prompt / task given:** “Implement a Java Spring Boot document service with ADMIN, OWNER and VIEWER roles, named per-document READ/EDIT grants, immediate revocation, and explicit server-side checks for metadata, downloads, edits, deletion and permission changes. Do not rely on UI hiding.”
- **What the AI produced:** `CurrentUserService`, `DocumentService`, document grant entities/repositories, and REST endpoints. The owner is the only person who can manage grants or delete; an EDIT grantee can update metadata/content; a READ grantee can only read/download.
- **What was wrong:** A source review caught a response-mapping bug in the first draft: the `isOwner` flag was initially being derived from `owner || EDIT`, which could make an EDIT grantee appear to be the owner in the UI. That could expose owner-only controls even though the server still rejected those operations.
- **What was done instead:** Corrected the DTO mapping so `isOwner` and `canManage` are true only for the owner, while `canEdit` is true for owner or EDIT grantee. Kept owner-only enforcement in the service. Added tests for unshared metadata, download, edit and delete attempts.
- **Code to inspect:** `DocumentService.toResponse`, `readableDocument`, `editableDocument`, `ownedDocument`; `DocumentAuthorizationTests.viewerCannotReadDownloadEditOrDeleteAnUnsharedDocument`.
- **Commit link:** **PENDING — add the real public commit URL after committing the reviewed fix.**

## 2026-11-04 — audit integrity and denied attempts

- **Prompt / task given:** “Design an audit log that records successes and denied document-access attempts, resists database-only row edits/reordering, serializes concurrent appends, and can be verified by an administrator.”
- **What the AI produced:** `AuditService`, `AuditEvent`, `AuditChainState`, a pessimistic lock on the singleton chain-state row, and an HMAC-SHA256 chain using a configured secret.
- **What was wrong / risk identified:** A plain SHA-256 hash chain is not sufficient against an attacker who can rewrite the database and recompute hashes. A database-only chain also cannot detect restoration of an older, internally consistent snapshot if the chain head is rolled back with it. The first persistence draft also attempted to insert the row before setting its non-null event hash.
- **What was done instead:** The implementation uses an HMAC key configured outside the database, signs the event before inserting it (no temporary null hash), locks the chain head while appending, writes denied events using `REQUIRES_NEW` before the request returns its denial, and adds PostgreSQL trigger/privilege protection with separate migration and runtime DB roles. The snapshot-rollback gap is explicitly documented in `THREATS.md`; an independent WORM checkpoint remains unfinished.
- **Code to inspect:** `AuditService.append`, `AuditService.sign`, `AuditService.verify`, `AuditChainStateRepository.lockSingleton`.
- **Commit link:** **PENDING — add the real public commit URL after committing the reviewed fix.**

## 2026-11-04 — encrypted private file storage and upload validation

- **Prompt / task given:** “Store uploads outside the public web root, prevent path traversal, accept only a small allowlist with signature checks, and encrypt files at rest using a separate key.”
- **What the AI produced:** `PrivateFileStorage` with random UUID storage keys, extension/signature checks, UTF-8 validation for TXT, and AES-256-GCM encryption with a random 96-bit nonce per file.
- **What was wrong:** Review found an early version that wrote plaintext to disk and read a header through an unclosed input stream. A filesystem path returned by storage would also be too easy to expose directly from a controller if the download path were implemented carelessly.
- **What was done instead:** Changed storage to persist only authenticated ciphertext; the file is fully authenticated/decrypted only after `DocumentService` authorises the caller. The controller returns the bytes as an attachment rather than exposing a static path. The 10 MiB cap bounds the memory buffer. Production key rotation/KMS is still not included.
- **Code to inspect:** `PrivateFileStorage.store`, `writeEncrypted`, `readDecrypted`, `resolve`; `DocumentController.download`.
- **Commit link:** **PENDING — add the real public commit URL after committing the reviewed fix.**

## 2026-11-04 — browser interface and untrusted data rendering

- **Prompt / task given:** “Build a usable browser UI for login, search, upload, sharing/revocation, user administration and audit verification; keep it same-origin and do not place authority in the browser.”
- **What the AI produced:** `index.html`, `app.css` and `app.js`, with CSRF header handling and role-specific navigation.
- **What was wrong:** The initial UI draft had a per-button inline style, which would be blocked by the chosen restrictive Content Security Policy. Any user-controlled title, description or filename rendered with unescaped `innerHTML` would create an XSS path.
- **What was done instead:** Moved styling into a CSS class, added `escapeHtml` for all dynamic text inserted into templates, and left API authorization in the server services. The UI is convenience only; direct API calls remain protected.
- **Code to inspect:** `app.js` `escapeHtml`, `renderDocumentModal`, table renderers; `SecurityConfig` CSP; `index.html` external script/style references.
- **Commit link:** **PENDING — add the real public commit URL after committing the reviewed fix.**

## 2026-11-04 — adversarial tests for IDOR and revocation

- **Prompt / task given:** “Write integration tests that fail if document authorisation is removed. Include direct UUID requests, downloads, writes/deletes, grant revocation, admin endpoints, audit tampering and upload validation.”
- **What the AI produced:** `DocumentAuthorizationTests`, using MockMvc and a real H2/Flyway application context.
- **What was wrong:** A superficial test of only the list screen would not test the actual IDOR boundary. It would still pass if a direct `GET /{id}/download` or `PUT /{id}` were accidentally made public. Testing only a hidden button would be similarly ineffective.
- **What was done instead:** Added direct endpoint tests for metadata, download, edit, delete, grant/revoke and admin access; included a test that mutates a signed audit row and expects HMAC verification to fail. Tests have been authored but could not be executed in this environment because Maven is not installed and external dependency resolution is unavailable. Run `mvn test` before submission and add results here.
- **Code to inspect:** `DocumentAuthorizationTests`.
- **Commit link:** **PENDING — add the real public commit URL after committing the reviewed tests.**

## 2026-11-04 — disabled-session checks and document-list audit events

- **Prompt / task given:** “Re-check account enabled state from the database on every service request, record use of a session after its account is disabled, and ensure document-list/search access is visible in the audit trail.”
- **What the AI produced:** An update to `CurrentUserService`, a `DOCUMENT_LIST` audit event, and additional integration tests for disabled sessions, administrator non-access and the EDIT-vs-owner boundary.
- **What was wrong / risk identified:** The initial implementation already denied disabled users at the service layer, but a stale authenticated session attempt did not leave its own denial event. Listing documents also returned metadata without an explicit successful list/search event.
- **What was done instead:** Added a `REQUIRES_NEW` denial event for a disabled account's old session, recorded successful document summaries returned by search, and added direct tests for those boundaries. The status-update DTO also validates that the `enabled` property is supplied instead of silently interpreting a missing field as `false`.
- **Code to inspect:** `CurrentUserService.requireUser`, `DocumentService.list`, `AdminController.status`, `DocumentAuthorizationTests`.
- **Commit link:** **PENDING — add the real public commit URL after committing the reviewed fix.**

## Reusable AI context / instructions

When asking an AI tool to modify this repository, reuse these constraints:

1. Treat every browser/API input, filename, document ID, header and metadata field as untrusted.
2. Enforce authorisation in service methods against the current database user and current document grant. UI hiding, UUIDs, client roles and cached permissions are never authority.
3. Document owner is the only grant manager and deleter. ADMIN is not a document superuser. READ cannot mutate. EDIT cannot share or delete.
4. A revocation must affect the next API request. Do not introduce pre-signed/download links or cache grants.
5. Add a denied audit event before returning an authorization denial. Do not claim a hash chain is immutable without an external secret/checkpoint.
6. Never store uploaded files under static resources or use a user filename as a disk path. Preserve AES-GCM integrity and validate file signatures server-side.
7. Add tests for each threat entry and update `THREATS.md`, `API.md`, `WRITEUP.md` and this log when behaviour changes.
8. Do not fabricate public URLs, commit hashes, deployed status, test results, screenshots or a completed demo.
