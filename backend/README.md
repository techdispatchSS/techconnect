# TechDispatch — backend

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
| `TECHDISPATCH_JWT_SECRET` | HMAC signing key, **32+ bytes**. `openssl rand -base64 48` |
| `TECHDISPATCH_BOOTSTRAP_ADMIN_EMAIL` | First-run Manager (see below) |
| `POSTGRES_PORT` | Host port of the containerised Postgres |

Then:

```bash
POSTGRES_PORT=5434 ./mvnw spring-boot:run
```

## First run — getting in

Only a Manager can create users, so a fresh database has no way in. On startup, if no
`MANAGER` exists and `techdispatch.bootstrap-admin.email` is set, the app creates one in
`PENDING_ACTIVATION` and logs a single-use activation link:

```
============================================================
 Bootstrap administrator created: admin@techdispatch.local
 Set the password using this single-use link (expires in 72h):

 http://localhost:4200/activate?token=...
============================================================
```

Open that link in the frontend to set the password, then sign in. The same email is also
delivered to Mailpit at <http://localhost:8025>.

This is idempotent — restarting never creates a second Manager or reissues a live token.

## Authentication model

TechDispatch issues its **own** JWTs (PRD FR-01, §8.1, §9.2). There is no external identity
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
