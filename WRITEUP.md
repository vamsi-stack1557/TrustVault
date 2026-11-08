# TrustVault — implementation write-up

## What is built

TrustVault is a Java 21 / Spring Boot 3.5 application with a same-origin browser UI and JSON API. It supports session authentication, three roles, named per-document READ/EDIT grants, upload/search/edit/download/soft-delete workflows, account administration, and an administrative audit view. A seeded sample document demonstrates the owner-to-viewer sharing path.

## Permission model in my own words

The database has one owner per document and zero or more grants from that document to a named user. An owner always has full control of their own document. A READ grantee can see the metadata and download the file. An EDIT grantee can do that and also replace content or edit metadata. Only the owner can grant/revoke or delete. The administrator can manage accounts and inspect audit activity, but does not automatically get document content access. There is no folder inheritance or group permission model.

The decision is made in `DocumentService` on every request, using the current user loaded from the database and the current grant row. The download endpoint does not redirect to a permanent file URL. Revoking a grant deletes the row; the next protected request fails. It cannot recall an already downloaded copy or bytes from a download already in progress.

## File storage and audit evidence

Uploads are limited to PDF, PNG, JPG/JPEG and UTF-8 TXT up to 10 MiB. The extension and signature are checked server-side. The blob is encrypted with AES-256-GCM using a fresh random nonce and a separate 32-byte key. It is stored outside the web root under a random UUID key, not a user-provided filename.

Audit events record successful and denied actions. Each event is HMAC-SHA256 signed with a previous-hash link. A database row lock serializes writes. The HMAC key is configured outside the database. Admins can run a verification endpoint/UI. A database rollback to an older valid snapshot is not detectable without an independent external checkpoint; this limitation is called out in `THREATS.md`.

## AI use and correction

AI assistance was used for the initial project structure, API/service implementation, storage and audit design, UI wiring, documentation, and tests. The prompt log records specific review findings: the first DTO mapping conflated owner and EDIT grantee, the audit design needed an external HMAC key and still has a rollback gap, the upload implementation needed authenticated encryption and safe file handling, the UI needed to avoid CSP-blocked inline styling and escape dynamic data, and the tests needed direct endpoint IDOR cases rather than only UI checks. See `PROMPTS.md` for the exact task summaries and review actions.

## Deliberately not built

No public registration, external sharing links, folders/groups, multi-version restore UI, MFA, password reset, login rate limiting, antivirus/CDR, upload quota, retention automation, KMS key rotation, external immutable audit checkpoint, audit export pipeline, or production TLS/reverse-proxy configuration. The administrator is intentionally not a superuser for documents. A smaller, document-scoped permission model is easier to reason about and test than inherited folder ACLs.

## What remains unfinished

- Execute `mvn test` in an environment with Maven/dependency access and resolve any failures before judging.
- Deploy to a public HTTPS URL with PostgreSQL, production secrets, a secure cookie, durable private storage, backup/restore, and external audit checkpoints.
- Add independent security review / penetration testing, rate limiting, MFA and malware scanning.
- Push a public Git repository with genuine incremental commits across the actual challenge week; replace the pending commit references in `PROMPTS.md`.
- Record a five-minute demo video and put its link in the final README.

This archive has not been deployed or independently penetration-tested. No live URL or demo video is claimed here.
