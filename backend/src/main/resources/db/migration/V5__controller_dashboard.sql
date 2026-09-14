-- Controller dashboard (FR-04): dispatch detail fields, incident geo-coordinates for
-- distance-based technician ranking, and the technician skill roster used to score
-- dispatch candidates against a job's required skills.

ALTER TABLE incidents
    ADD COLUMN site_latitude  NUMERIC(9, 6),
    ADD COLUMN site_longitude NUMERIC(9, 6);

ALTER TABLE incidents
    ADD CONSTRAINT incidents_priority_check
        CHECK (priority IS NULL OR priority IN ('HIGH', 'MEDIUM', 'LOW'));

ALTER TABLE dispatches
    ADD COLUMN job_type                VARCHAR(64),
    ADD COLUMN required_skills         TEXT,
    ADD COLUMN required_certifications TEXT,
    ADD COLUMN sla_response            VARCHAR(64),
    ADD COLUMN site_contact            VARCHAR(255),
    ADD COLUMN notes_for_technician    TEXT;

-- One row per skill or certification a technician holds. Certifications (e.g. "Cisco CCNA")
-- are stored in this same roster rather than a separate table: a dispatch's required-skills
-- and required-certifications fields are both matched against it when ranking candidates.
CREATE TABLE technician_skills (
    id         UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill      VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT technician_skills_unique UNIQUE (user_id, skill)
);

CREATE INDEX technician_skills_user_idx ON technician_skills (user_id);
