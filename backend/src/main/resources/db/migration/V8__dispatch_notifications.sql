-- Technicians are told about a dispatch (and can be told again), so the Controller can see
-- how many times they have been notified and when. Existing dispatches predate any
-- notification being sent, so they start at zero rather than pretending one went out.
ALTER TABLE dispatches
    ADD COLUMN notification_count INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN last_notified_at   TIMESTAMPTZ;
