# TrustVault — five-minute demo recording script

Record this against the deployed HTTPS instance after changing every demo/default secret. Do not show real confidential documents, private keys, or production passwords in the recording.

## 0:00–0:35 — What TrustVault protects

Show the login page and state the three roles. Explain that access is document-scoped: the administrator manages accounts and audits, the owner controls their own documents, and a viewer can only see a document explicitly shared with them. Point out that there are no permanent file URLs.

## 0:35–1:15 — Owner upload and search

Sign in as an OWNER. Upload a small harmless TXT or PDF file, enter a title/category/description, and show the new document in the list. Search by title, owner and category, then show the date filters.

## 1:15–2:00 — Named permission grant

Open the uploaded document. Grant READ to a separate VIEWER account. Sign out and sign in as that viewer. Show that the shared document appears and can be downloaded. Explain that the server checks the current grant on every metadata and download request.

## 2:00–2:50 — Demonstrate a denied IDOR attempt

Use browser developer tools or an API client authenticated as the viewer. Request a different document UUID that was not shared. Show the 404 response. Try a direct download URL for that same UUID and show that it is also denied. Do not rely on hidden buttons as the demonstration.

## 2:50–3:30 — Immediate revocation

Sign back in as the owner and revoke the viewer's READ grant. Return to the viewer session and repeat the metadata and download requests. Both should now fail. Explain that revocation blocks future API requests but cannot recall a file already downloaded or stop a transfer already authorised and in progress.

## 3:30–4:25 — Audit evidence

Sign in as ADMIN. Show account management and the audit activity view. Point out upload, permission-grant, document-list, download and denied-access events. Run “Verify audit chain” and show the result. Explain HMAC-SHA256 links, the separate key, the append-only PostgreSQL trigger and the independent external checkpoint that is still an operational follow-up.

## 4:25–5:00 — Security boundaries and unfinished work

Briefly show `THREATS.md`, `API.md` and the integration tests. State what is deliberately not included: MFA, malware scanning, key rotation/KMS, rate limiting, external immutable audit checkpoints and production penetration testing. Finish by showing the local test command and the deployment configuration.
