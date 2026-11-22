-- The phones and browsers a technician has turned job alerts on for (#69). One user can have
-- several, and a token belongs to whoever registered it last: a shared phone must not keep
-- showing the previous person's job offers.
CREATE TABLE push_devices (
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id),
    token        TEXT        NOT NULL UNIQUE,
    platform     VARCHAR(16) NOT NULL CHECK (platform IN ('WEB', 'ANDROID', 'IOS')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX push_devices_user_idx ON push_devices (user_id);
