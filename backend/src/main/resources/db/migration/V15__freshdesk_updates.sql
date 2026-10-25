-- What TechConnect tells Freshdesk about an incident's ticket as the work moves along: private
-- notes (dispatched, accepted, on site, report, ...) and, on close, resolving the ticket.
--
-- Every update is written here in the same transaction as the change it reports, so nothing is
-- lost if Freshdesk is down. A sender works through the PENDING rows in id order. In demo mode
-- the rows are recorded as DEMO and never sent: the dashboard shows exactly what would have gone
-- out, without touching the real Freshdesk.
CREATE TABLE freshdesk_updates (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id UUID         NOT NULL REFERENCES incidents (id),
    ticket_id   VARCHAR(64)  NOT NULL,
    event       VARCHAR(32)  NOT NULL,
    action      VARCHAR(16)  NOT NULL,
    body        TEXT,
    delivery    VARCHAR(16)  NOT NULL,
    attempts    INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ
);

CREATE INDEX freshdesk_updates_incident ON freshdesk_updates (incident_id, id);
CREATE INDEX freshdesk_updates_pending ON freshdesk_updates (id) WHERE delivery = 'PENDING';
