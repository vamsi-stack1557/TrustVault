# TrustVault API reference

All `/api/**` endpoints except `GET /api/auth/csrf` and `POST /api/auth/login` require an authenticated session. Mutating requests require the CSRF token returned by `GET /api/auth/csrf` in the response header named by `headerName`. The browser app handles this automatically. Requests and responses use JSON unless marked `multipart/form-data`.

## Authentication

| Method | Path | Allowed caller | Purpose |
|---|---|---|---|
| GET | `/api/auth/csrf` | Public | Obtain CSRF token/header name |
| POST | `/api/auth/login` | Public + CSRF | Authenticate with `{ "email", "password" }`; creates an HTTP session |
| POST | `/api/auth/logout` | Authenticated | Invalidate current session |
| GET | `/api/me` | Authenticated active account | Current account summary |

## Documents

| Method | Path | Allowed caller | Purpose |
|---|---|---|---|
| GET | `/api/documents` | OWNER or VIEWER | List only owned/shared documents (the list/search read is audited); filters: `q`, `owner`, `category`, `from`, `to` (ISO dates) |
| POST | `/api/documents` | OWNER | Upload multipart fields `file`, `title`, `category`, optional `description` |
| GET | `/api/documents/{id}` | Owner or active READ/EDIT grantee | Read metadata |
| GET | `/api/documents/{id}/download` | Owner or active READ/EDIT grantee | Download encrypted-at-rest file after a fresh authorization check |
| PUT | `/api/documents/{id}` | Owner or active EDIT grantee | Update JSON metadata `{ "title", "category", "description" }` |
| PUT | `/api/documents/{id}/content` | Owner or active EDIT grantee | Replace multipart file field `file` |
| DELETE | `/api/documents/{id}` | Owner only | Soft-delete document |
| GET | `/api/documents/{id}/permissions` | Owner only | List grants for this document |
| POST | `/api/documents/{id}/permissions` | Owner only | Grant/update JSON `{ "email", "permission": "READ" | "EDIT" }` for an active non-admin account |
| DELETE | `/api/documents/{id}/permissions/{grantId}` | Owner only | Revoke a grant |

A document that is missing, deleted, or not readable by the caller returns `404` for the protected document endpoints to reduce resource enumeration. An authenticated caller that lacks an administrative role receives `403` from administrative endpoints.

## Administration

| Method | Path | Allowed caller | Purpose |
|---|---|---|---|
| GET | `/api/admin/overview` | ADMIN | Aggregate counts (users, active documents, grants, audit events) |
| GET | `/api/admin/users` | ADMIN | List account metadata |
| POST | `/api/admin/users` | ADMIN | Create OWNER/VIEWER account; ADMIN role is rejected |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | Enable/disable user with `{ "enabled": true | false }`; cannot disable own or last active ADMIN account |
| GET | `/api/admin/audit?limit=100` | ADMIN | Read newest audit events; `limit` clamped to 1–500 |
| GET | `/api/admin/audit/verify` | ADMIN | Verify previous-hash links, HMACs, and stored chain head |

## Error responses

Errors are JSON, generally `{ "message": "..." }` or `{ "error": "..." }`. Common statuses: `400` invalid input/upload; `401` unauthenticated; `403` authenticated but role not permitted; `404` missing/unreadable document; `409` duplicate email; `413` upload too large. No endpoint accepts a document storage key or raw filesystem path from the caller.
