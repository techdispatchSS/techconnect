-- TechDispatch MVP schema.
-- Derived from PRD §6 (Functional Requirements), §8 (Service Contracts) and FR-09 (Audit Trail).
--
-- Enumerated columns are varchar + CHECK rather than native PG enum types: adding a value to a
-- native enum needs ALTER TYPE (which cannot run inside a transactional migration on older PG),
-- whereas a CHECK constraint is trivially replaced by a later migration. They map directly to
-- JPA @Enumerated(EnumType.STRING).

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

-- Users are NEVER hard-deleted. Offboarding (M-06) is a soft-disable via status = 'DISABLED':
-- a real delete would break the FKs from jobs / job_status_history / dispatch_responses and
-- violate FR-09's "audit records shall not be editable or deletable by any user".
CREATE TABLE users (
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    email                 VARCHAR(255) NOT NULL,
    -- Nullable: a user exists in PENDING_ACTIVATION before they have ever set a password.
    -- The admin never sees or handles a password; the user sets it via an activation link.
    password_hash         VARCHAR(72),
    name                  VARCHAR(255) NOT NULL,
    phone                 VARCHAR(32),
    role                  VARCHAR(16)  NOT NULL,
    status                VARCHAR(24)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    -- Bumped on deactivate / password change / reset. The JWT carries the value it was
    -- minted with; a mismatch means the token is stale, which is what makes an 8h token
    -- stop working the moment a Manager offboards someone.
    token_version         INTEGER      NOT NULL DEFAULT 0,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    activated_at          TIMESTAMPTZ,
    created_by            UUID         REFERENCES users (id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check   CHECK (role IN ('CONTROLLER', 'TECHNICIAN', 'MANAGER')),
    CONSTRAINT users_status_check CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DISABLED'))
);

-- Case-insensitive uniqueness: email is the login identity, so Bob@x.com and bob@x.com
-- must not be two accounts.
CREATE UNIQUE INDEX users_email_unique_idx ON users (lower(email));
CREATE INDEX users_role_status_idx ON users (role, status);

-- Single-use, expiring tokens backing account activation and password reset.
-- Only the SHA-256 hash is stored, so a database leak does not hand out working links.
CREATE TABLE user_activation_tokens (
    id          UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    purpose     VARCHAR(24) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_activation_tokens_purpose_check
        CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET'))
);

CREATE UNIQUE INDEX user_activation_tokens_hash_idx ON user_activation_tokens (token_hash);
CREATE INDEX user_activation_tokens_user_idx ON user_activation_tokens (user_id);

-- FR-09 extended to administrative actions. FR-09's wording covers job status changes, but
-- "who granted whom access" is what makes M-06 defensible to a client. Insert-only.
CREATE TABLE admin_audit_log (
    id             UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    actor_user_id  UUID        NOT NULL REFERENCES users (id),
    action         VARCHAR(32) NOT NULL,
    target_user_id UUID        REFERENCES users (id),
    details        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT admin_audit_log_action_check CHECK (action IN (
        'USER_CREATED', 'USER_UPDATED', 'USER_ROLE_CHANGED',
        'USER_DEACTIVATED', 'USER_REACTIVATED', 'INVITE_RESENT'
    ))
);

CREATE INDEX admin_audit_log_target_idx  ON admin_audit_log (target_user_id, created_at DESC);
CREATE INDEX admin_audit_log_created_idx ON admin_audit_log (created_at DESC);

-- One row per TECHNICIAN user, provisioned at onboarding with status OFFLINE.
CREATE TABLE technician_profiles (
    user_id          UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    status           VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    last_latitude    NUMERIC(9, 6),
    last_longitude   NUMERIC(9, 6),
    last_location_at TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT technician_profiles_status_check
        CHECK (status IN ('AVAILABLE', 'ON_JOB', 'OFFLINE'))
);

CREATE INDEX technician_profiles_status_idx ON technician_profiles (status);

-- ---------------------------------------------------------------------------
-- Incidents (FR-02, FR-03)
-- ---------------------------------------------------------------------------

CREATE TABLE incidents (
    id                  UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    freshdesk_ticket_id VARCHAR(64),
    client_name         VARCHAR(255) NOT NULL,
    site_address        TEXT,
    contact_name        VARCHAR(255),
    contact_phone       VARCHAR(32),
    issue_type          VARCHAR(128),
    priority            VARCHAR(16),
    sla                 VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    description         TEXT,
    infrastructure_type VARCHAR(128),
    inventory_notes     TEXT,
    additional_notes    TEXT,
    sla_due_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT incidents_status_check
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'ON_HOLD', 'OVERDUE', 'CLOSED'))
);

-- Partial unique index: many incidents are controller-logged phone reports with no Freshdesk
-- ticket, and a plain UNIQUE would allow only one such NULL-free row per backend.
CREATE UNIQUE INDEX incidents_freshdesk_ticket_idx
    ON incidents (freshdesk_ticket_id) WHERE freshdesk_ticket_id IS NOT NULL;
CREATE INDEX incidents_status_created_idx ON incidents (status, created_at DESC);

-- ---------------------------------------------------------------------------
-- Dispatch (FR-04)
-- ---------------------------------------------------------------------------

CREATE TABLE dispatches (
    id            UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    incident_id   UUID        NOT NULL REFERENCES incidents (id),
    dispatch_type VARCHAR(16) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- FR-04: 10-minute response window from broadcast to acceptance.
    expires_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID        REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT dispatches_type_check   CHECK (dispatch_type IN ('BROADCAST', 'ASSIGN')),
    CONSTRAINT dispatches_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
);

CREATE INDEX dispatches_incident_idx ON dispatches (incident_id);
CREATE INDEX dispatches_pending_idx  ON dispatches (status, expires_at) WHERE status = 'PENDING';

CREATE TABLE dispatch_responses (
    id            UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    dispatch_id   UUID        NOT NULL REFERENCES dispatches (id) ON DELETE CASCADE,
    technician_id UUID        NOT NULL REFERENCES users (id),
    response      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    responded_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT dispatch_responses_response_check
        CHECK (response IN ('PENDING', 'ACCEPTED', 'DECLINED', 'NO_RESPONSE')),
    CONSTRAINT dispatch_responses_unique UNIQUE (dispatch_id, technician_id)
);

CREATE INDEX dispatch_responses_technician_idx ON dispatch_responses (technician_id);

-- ---------------------------------------------------------------------------
-- Jobs (FR-05, FR-06, FR-07)
-- ---------------------------------------------------------------------------

CREATE TABLE jobs (
    id               UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    incident_id      UUID        NOT NULL REFERENCES incidents (id),
    dispatch_id      UUID        REFERENCES dispatches (id),
    technician_id    UUID        NOT NULL REFERENCES users (id),
    status           VARCHAR(16) NOT NULL DEFAULT 'EN_ROUTE',
    -- FR-06: recorded automatically, not entered by the technician.
    started_at       TIMESTAMPTZ,
    arrived_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    resolution_notes TEXT,
    root_cause       TEXT,
    recommendations  TEXT,
    labour_hours     NUMERIC(6, 2),
    submitted_at     TIMESTAMPTZ,
    closed_by        UUID        REFERENCES users (id),
    closed_at        TIMESTAMPTZ,
    closing_notes    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT jobs_status_check CHECK (status IN (
        'EN_ROUTE', 'ON_SITE', 'WORK_STARTED', 'ON_HOLD',
        'WORK_RESUMED', 'COMPLETE', 'SUBMITTED', 'CLOSED'
    ))
);

CREATE INDEX jobs_technician_status_idx ON jobs (technician_id, status);
CREATE INDEX jobs_incident_idx          ON jobs (incident_id);
CREATE INDEX jobs_status_idx            ON jobs (status);

CREATE TABLE parts_used (
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    job_id        UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    part_name     VARCHAR(255) NOT NULL,
    quantity      INTEGER      NOT NULL DEFAULT 1,
    -- FR-06: optional in the MVP.
    serial_number VARCHAR(128),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT parts_used_quantity_check CHECK (quantity > 0)
);

CREATE INDEX parts_used_job_idx ON parts_used (job_id);

-- FR-09 / §9.5: every job status change logged with user, timestamp and previous state.
-- Insert-only — no update or delete path is exposed anywhere in the application.
CREATE TABLE job_status_history (
    id              UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    job_id          UUID        NOT NULL REFERENCES jobs (id),
    status          VARCHAR(16) NOT NULL,
    previous_status VARCHAR(16),
    reason          TEXT,
    changed_by      UUID        NOT NULL REFERENCES users (id),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX job_status_history_job_idx ON job_status_history (job_id, changed_at DESC);
