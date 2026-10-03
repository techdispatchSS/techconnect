# TechConnect — backend

Spring Boot 4 / Java 21 API. Base URL `/api/v1` (PRD §8).

## Running locally

```bash
docker compose up -d postgres mailpit
```

> **Port note:** if you also run PostgreSQL natively it already owns 5432 and will silently
> shadow the container's published port — the app then authenticates against the wrong
> database and fails with `password authentication failed`. Set `POSTGRES_PORT` in `.env` to
> a free port (5434 works) rather than stopping your local service.

Copy `.env.example` to `.env` and set at least:

| Variable | Purpose |
|---|---|
| `TECHCONNECT_JWT_SECRET` | HMAC signing key, **32+ bytes**, **required — no default**. `openssl rand -base64 48` |
| `TECHCONNECT_BOOTSTRAP_ADMIN_EMAIL` | First-run Manager (see below) |
| `POSTGRES_PORT` | Host port of the containerised Postgres |

`TECHCONNECT_JWT_SECRET` has no fallback value — the app refuses to start without it, in
any environment including local dev. This is deliberate: a default that actually works is a
secret published in source control, and anyone who reads the repo could forge a valid token
for any user. Generate a fresh one per environment; never reuse a secret across dev, staging,
and prod, and never commit a real value into `.env.example`.

Then:

```bash
POSTGRES_PORT=5434 ./mvnw spring-boot:run
```

## First run — getting in

Only a Manager can create users, so a fresh database has no way in. On startup, if no
`MANAGER` exists and `techconnect.bootstrap-admin.email` is set, the app creates one in
`PENDING_ACTIVATION` and logs a single-use activation link:

```
============================================================
 Bootstrap administrator created: admin@tech-connect.app
 Set the password using this single-use link (expires in 72h):

 http://localhost:4200/activate?token=...
============================================================
```

Open that link in the frontend to set the password, then sign in. The same email is also
delivered to Mailpit at <http://localhost:8025>.

This is idempotent — restarting never creates a second Manager or reissues a live token.

## Authentication model

TechConnect issues its **own** JWTs (PRD FR-01, §8.1, §9.2). There is no external identity
provider — no Cognito, no AWS IAM. The `users` table is the only user directory.

- Passwords: bcrypt, cost factor 12. Never set by an administrator — users choose their own
  through a single-use activation link.
- Tokens: HMAC-SHA256, 8-hour expiry, no refresh token (Phase 2).
- **Revocation:** each token carries a `tv` (token version) claim. Offboarding a user, changing
  their role, or resetting their password increments `users.token_version`, so tokens minted
  earlier stop working on the very next request rather than lingering for up to 8 hours. The
  auth filter costs one indexed primary-key lookup per request to enforce this.
- Login is throttled: 5 consecutive failures lock the account for 15 minutes.

### What a failed login reveals

The password is checked **before** account status, and the reason for a refusal is disclosed
only once the password was correct:

| Situation | Response |
|---|---|
| Unknown email | `Invalid credentials` |
| Wrong password (any account state) | `Invalid credentials` |
| Never activated | `Invalid credentials` |
| **Correct** password, account deactivated | "Your account has been deactivated…" |
| **Correct** password, account locked | "Too many failed sign-in attempts…" |

This is a deliberate, narrow divergence from PRD §8.1, which specifies the generic message for
every 401. Announcing "this account is deactivated" to any caller would make the login endpoint
a staff directory — probe addresses, learn who works here and who used to. Requiring the correct
password first means a genuine offboarded technician gets a straight answer while an attacker
learns nothing they did not already know.

### Addresses

Every user has an `address`, required when onboarding through the portal. For technicians it is
the origin point for distance-based job matching (PRD Phase 2). **Only a Manager can change it** —
`PUT /auth/me` silently ignores an address submitted by any other role, because a technician
editing their own address would be editing which jobs they get offered.

Distance matching will additionally need geocoded coordinates. That is an additive migration
(`address_latitude` / `address_longitude`) to make when the dispatch work lands, not now.

## Technician applications

A prospective technician applies at `/apply` (no account, no login) and uploads proof of their
certificates and qualifications. Only a **Manager** can accept or reject; accepting creates the
Technician account through the same path as "Add user" (pending activation + emailed invite).
Applications are a separate table from `users` and share nothing with `/v1/auth`.

| | |
|---|---|
| `POST /v1/applications` | Public. Multipart: an `application` JSON part and one or more `files`. Always `202` with the same body. |
| `GET /v1/admin/applications` · `/{id}` · `/pending-count` | Manager only. |
| `GET /v1/admin/applications/{id}/documents/{docId}` | Manager only. Served as an attachment; audited. |
| `POST /v1/admin/applications/{id}/approve` · `/reject` | Manager only. Decided once (`409` after). Reject needs a reason. |

**A rejection is permanent.** There is one application per person, ever: unique indexes on a
*normalised* email (case, `+tag`, and Gmail dots folded) and a normalised phone number (`+27`/`0`
folded) mean a rejected applicant can't reapply by retyping their details. The record and its
documents are kept. A repeat submission gets the *same* `202` as a first one and sends no email,
so the form can't be used to discover who has applied, or to mail-bomb an address.

### Securing the public upload

This is the only unauthenticated endpoint that accepts files, so it is hardened in layers:

- **Before the body is read** (`ApplicationSubmissionFilter`, a servlet filter — multipart is
  parsed before any controller runs): per-address and global rate limits (`429` + `Retry-After`),
  and a request is refused unless it declares a `Content-Length` within the ceiling.
- **Real client address** (`ClientIp`): counted in from the *right* of `X-Forwarded-For` by the
  number of proxies we run (`techconnect.security.trusted-proxy-hops`), so a forged header can't
  pick the rate-limit key. `1` in `prod` (the ALB) and docker-compose; `0` for a bare local run.
- **Files are never trusted** (`UploadInspector`): type is detected from the bytes (PDF/JPG/PNG
  only) and must agree with the claimed content type *and* extension; images need valid headers,
  sane dimensions and no data after the end marker; PDFs with JavaScript, launch/open actions,
  embedded files or encryption — including `#xx`-obfuscated names — are refused. The client's
  filename is sanitised for display only and never used in a path; storage keys are random UUIDs.
  This is **not** a virus scanner (it can't see inside compressed PDF object streams) — add
  ClamAV or an S3-triggered scan before relying on it for anything stronger.
- **Storage**: private bucket, AES-256 at rest, no public URLs. Managers fetch documents through
  an authenticated endpoint that sets `Content-Disposition: attachment`, `nosniff`, `no-store`
  and a `sandbox` CSP, and the UI only ever downloads them. Opening one is audited.
- **Input**: tight patterns and lengths on every field, control characters stripped, a honeypot
  field, and a mandatory consent checkbox.

**Deploying:** the S3 bucket must have *Block all public access* on; the proxy in front of the
API must allow bodies of at least 32 MB (`client_max_body_size` — already set in
`frontend/nginx.conf`); and if anything other than the single ALB sits in front of the API, set
`TECHCONNECT_TRUSTED_PROXY_HOPS` to the real number of proxies, or every applicant will share one
rate-limit bucket.

## Migrations

Flyway owns the schema; Hibernate runs with `ddl-auto=validate` and must never alter it.
Add migrations as `src/main/resources/db/migration/V{n}__{description}.sql`.

Users are **never** hard-deleted. Offboarding sets `status = DISABLED`, because `jobs`,
`job_status_history` and `dispatch_responses` hold foreign keys into `users` and FR-09 forbids
destroying audit history.

## Tests

```bash
./mvnw test
```

Integration tests run against a real PostgreSQL via Testcontainers (Docker must be running).
An in-memory database is not sufficient: the schema relies on `jsonb`, partial unique indexes,
and a functional unique index on `lower(email)`.
