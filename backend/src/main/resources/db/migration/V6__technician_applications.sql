-- Technician applications: a prospective technician applies through a public page (no
-- account, no login), uploading proof of their certificates and qualifications. Only a Manager
-- can accept or reject; accepting provisions the real `users` row (the same path as "Add user").
--
-- Applications are deliberately a separate table from `users`: an applicant has no password,
-- no role and no session, and a rejected applicant must never become a login-capable row.
--
-- Rows are NEVER deleted. A rejected application is kept permanently so the same person cannot
-- simply apply again — see the two unique indexes below.

CREATE TABLE technician_applications (
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    first_name          VARCHAR(255) NOT NULL,
    last_name           VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    -- Canonical form of `email` (lower-cased, "+tag" stripped, Gmail dots removed). What the
    -- one-application-per-person rule is enforced on, so a@x.com, A@x.com and a+2@x.com are
    -- one applicant, not three.
    email_normalized    VARCHAR(255) NOT NULL,
    phone               VARCHAR(32)  NOT NULL,
    -- Digits only, with the country prefix folded to the local form (0821234567 and
    -- +27 82 123 4567 are one number).
    phone_normalized    VARCHAR(32)  NOT NULL,
    address_street      VARCHAR(255) NOT NULL,
    address_suburb      VARCHAR(120) NOT NULL,
    address_city        VARCHAR(120) NOT NULL,
    address_province    VARCHAR(24)  NOT NULL,
    address_postal_code VARCHAR(4)   NOT NULL,
    experience_summary  TEXT         NOT NULL,
    skills              TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    decided_by          UUID         REFERENCES users (id),
    decided_at          TIMESTAMPTZ,
    decision_reason     TEXT,
    -- Set on approval: the account that was provisioned for this applicant.
    user_id             UUID         REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT technician_applications_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT technician_applications_decided_check
        CHECK (status = 'PENDING' OR (decided_at IS NOT NULL AND decided_by IS NOT NULL)),
    CONSTRAINT technician_applications_rejected_reason_check
        CHECK (status <> 'REJECTED' OR decision_reason IS NOT NULL)
);

-- One application per person, forever, whatever its outcome: this is what makes a rejection
-- permanent. Unique on BOTH identifiers so changing only the email (or only the phone number)
-- does not get a rejected applicant past the check. Also race-safe: two simultaneous
-- submissions cannot both win.
CREATE UNIQUE INDEX technician_applications_email_unique_idx
    ON technician_applications (email_normalized);
CREATE UNIQUE INDEX technician_applications_phone_unique_idx
    ON technician_applications (phone_normalized);

CREATE INDEX technician_applications_status_created_idx
    ON technician_applications (status, created_at DESC);

-- Proof of certificates and qualifications. The file itself lives in private object storage;
-- only its key and a fingerprint are kept here. No ON DELETE CASCADE: nothing here is deleted.
CREATE TABLE application_documents (
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    application_id    UUID         NOT NULL REFERENCES technician_applications (id),
    kind              VARCHAR(16)  NOT NULL,
    -- What the applicant calls it, e.g. "CCNA certificate".
    title             VARCHAR(200) NOT NULL,
    -- Sanitised for display only; never used to build a storage path.
    original_filename VARCHAR(255) NOT NULL,
    -- Detected from the file's own bytes, not taken from what the client claimed.
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT application_documents_kind_check
        CHECK (kind IN ('CERTIFICATE', 'QUALIFICATION')),
    CONSTRAINT application_documents_size_check CHECK (size_bytes > 0),
    CONSTRAINT application_documents_storage_key_unique UNIQUE (storage_key)
);

CREATE INDEX application_documents_application_idx ON application_documents (application_id);

-- Deciding on an application, and opening an applicant's documents (personal information
-- under POPIA), are audited like every other administrative action. APPLICATION_REJECTED has
-- no target user — a rejected applicant never gets a `users` row — so its details carry the
-- application id instead.
ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_action_check;

ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action_check CHECK (action IN (
    'USER_CREATED', 'USER_UPDATED', 'USER_ROLE_CHANGED',
    'USER_DEACTIVATED', 'USER_REACTIVATED', 'INVITE_RESENT',
    'SELF_PROFILE_UPDATED',
    'APPLICATION_APPROVED', 'APPLICATION_REJECTED', 'APPLICATION_DOCUMENT_VIEWED'
));
