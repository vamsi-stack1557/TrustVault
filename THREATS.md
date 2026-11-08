# THREATS.md — TrustVault attack review

This is the threat register for the submitted implementation. “Mitigation” describes code currently present. “Residual / accepted” records gaps that should not be hidden during judging. Re-test these against the deployed URL, not only the local app.

## Assets and trust boundaries

- Confidential document bytes and metadata.
- Per-document owner/grantee relationships and account status.
- Audit event content, ordering, chain head and HMAC key.
- Session cookies, CSRF token, password hashes, file-encryption key.
- Trust boundaries: browser → HTTP API; application → database; application → private file volume; application → secret store; administrator → account/audit views.

## Threats, mitigations and residual risk

| # | Threat / attack path | Mitigation in this build | Residual / accepted risk |
|---|---|---|---|
| 1 | IDOR: guess/replace a document UUID on metadata GET | `DocumentService.readableDocument` checks owner or current grant on each request; inaccessible IDs return 404 | UUID randomness is not relied on as authorization; add more tests for every new route |
| 2 | IDOR on direct download URL | Download controller calls the same service authorization check before reading/decrypting bytes; no static file URL exists | A download already authorized and in progress cannot be recalled |
| 3 | Viewer uses PUT metadata/content, DELETE, or permission endpoints | `editableDocument` and `ownedDocument` enforce distinct EDIT/owner rules server-side | Future controllers must not bypass `DocumentService` |
| 4 | Permission removed but old token/link still works | No pre-signed URLs or reusable bearer links; DB grant is read on every new request | Copies already downloaded cannot be recalled; current streams cannot be retroactively stopped |
| 5 | A grant for one file accidentally exposes a second file | Grants reference exactly one `document_id`; no folder ACL or inheritance exists; list query restricts to owner/grants | No group/folder workflow is provided |
| 6 | Admin account silently reads every document | Admin role is explicitly denied implicit document read; only audit/user metadata is exposed | An admin can still disable accounts or inspect metadata in audit records; tightly control ADMIN accounts |
| 7 | Grantee changes their own permission or grants access onward | Only document owner can list/grant/revoke; `ADMIN` and self-target are rejected as grantees | No delegated-sharing workflow |
| 8 | Grant target is disabled or privileged | Grant operation requires an active, non-admin named account; current account is reloaded from DB for service calls | Concurrent disable/revoke races are transaction/request-bound; high-assurance deployments may require stronger isolation/locking |
| 9 | Disabled user reuses an old session | `CurrentUserService` reloads the account and checks `enabled` and role in protected service operations | The generic authenticated filter still accepts the session, but no protected service action succeeds while disabled |
| 10 | Session fixation or stolen session via script | Login changes session ID; cookie is HttpOnly, SameSite=Strict; browser UI has a restrictive CSP | No MFA, device binding, forced global session revocation list, or idle-risk analytics |
| 11 | Cross-site request forgery | Spring Security CSRF protection with token header on state-changing requests; same-origin UI | Reverse-proxy/origin configuration must be reviewed on deployment |
| 12 | Cross-site scripting through title, description, owner email or filename | Browser rendering HTML-escapes all server-sourced values; restrictive CSP; content is served as attachment, never inline | Any new UI `innerHTML` must preserve escaping; browser security tests are not automated |
| 13 | Path traversal using original filename or storage key | Storage filenames are random UUIDs; original filename is sanitized and never used as a path; storage key syntax and normalized parent are checked | A compromised app process can still access the private storage directory |
| 14 | Upload executable/script disguised as an allowed file | Extension AND file signature are checked; allowed list is PDF/PNG/JPG/JPEG/UTF-8 TXT; files download as attachments with `nosniff` | No antivirus, CDR, PDF active-content sanitization, image re-encoding or malware sandbox. Polyglot files are possible; do not accept high-risk files without scanning |
| 15 | Oversized upload exhausts disk or memory | 10 MiB application file limit, Spring multipart limits, upload type allowlist | No per-user quota, rate limiting, concurrency cap, or automated storage cleanup; deploy with volume quotas and request limits |
| 16 | SQL injection through search filters or ids | JPA repository parameters and typed UUID/date parameters; no dynamic SQL concatenation from user input | Review every future native query |
| 17 | Audit row modified or forged by DB-only attacker | HMAC-SHA256 over event fields plus previous hash; key configured outside DB; transaction lock serializes append; PostgreSQL profile adds a trigger rejecting UPDATE/DELETE; admin verification checks every link and chain head | The PostgreSQL runtime role cannot update/delete audit rows or drop the trigger; a database superuser/schema owner can still disable it, and if the HMAC key is stolen an attacker can forge. A full old-database snapshot rollback is not detected without an externally stored checkpoint; use a distinct migration/runtime DB role and export chain head to WORM/independent system |
| 18 | Audit events reordered or deleted | Previous-hash links and stored chain head detect gaps/reordering/deletion during verification | Same rollback limitation as #17; external checkpoint is not implemented |
| 19 | Concurrent audit writes fork the chain | Singleton `audit_chain_state` row is pessimistically locked during append | Validate locking behaviour on production PostgreSQL; no distributed external audit writer |
| 20 | Denied IDOR attempt is not logged because request throws | Denied attempts are appended in `REQUIRES_NEW` before returning 404/403; successful document-list/search, metadata-read and download requests are also logged | Failures before controller/service invocation (e.g. malformed HTTP at proxy) are not all captured; add edge/proxy logs |
| 21 | Audit log injection via newline or oversized user-agent/details | Newlines/NULs are flattened and fields truncated before signing/storage | IP/user-agent are supplied by network/request and should be treated as untrusted; do not blindly trust forwarded headers |
| 22 | A successful download event is mistaken for proof bytes fully reached a person's device | Audit event means server authorised and decrypted a download request | It cannot prove the client persisted/fully consumed the bytes. Current event is written before the HTTP transfer completes |
| 23 | Disk stolen or backup copied | AES-256-GCM encryption at rest with a random 96-bit nonce per file; separate Base64 32-byte key | Key rotation/versioning and KMS integration are not implemented; default local key is public and must never be used in production |
| 24 | File ciphertext altered | GCM authentication tag is verified before a plaintext byte array is returned | Application currently buffers a maximum 10 MiB document in memory during upload/download |
| 25 | Weak demo/admin password reused | Demo accounts are clearly disclosed and password hashing uses BCrypt cost 12; deployment instructions require fresh users/secrets | No password reset flow, complexity policy beyond new-user min length, breach-password check, or login rate limit |
| 26 | Admin disables the only remaining admin | Service prevents disabling own account and last active ADMIN | No full administrator recovery / break-glass workflow |
| 27 | Soft-deleted document exposed through old UUID | All protected repository lookups require `deletedAt IS NULL`; deletion is owner-only | Blob retention/restore UI and retention policy are unfinished; deleted blobs require operational cleanup policy |
| 28 | App exposed over plaintext HTTP or database port exposed | Compose separates DB; session secure-cookie flag can be enabled; HSTS header is configured | TLS termination, network policy, backups, secrets, monitoring and firewall are deployment responsibilities, not bundled infrastructure |

## Security invariants

1. Never return metadata or bytes unless the current account is the owner or has an active grant for that exact document.
2. Never authorize from a hidden UI button, a client-supplied role, UUID secrecy, filename, or cached grant.
3. Only the owner manages that document's grants and deletion.
4. Revoke by deleting the grant row; every future request re-reads the row.
5. Denied document access attempts must be recorded before returning a denial.
6. Treat audit HMAC and file-encryption keys as independent secrets outside the DB and outside Git.

## Known omissions to discuss honestly

MFA, login/API rate limiting, user password reset/rotation, anti-virus/CDR, upload quotas, content version restore UI, automatic retention/deletion, immutable external audit checkpointing, audit export, object-lock backups, key rotation/KMS, TLS reverse-proxy config, deployment hardening, and an independent penetration test are not implemented in this archive. These are accepted to keep this seven-day challenge implementation small; they are not claims that those controls are unnecessary.
