-- The history of a ticket: who logged it, who dispatched it to whom, who was nudged, who
-- accepted or declined, when it lapsed, and who closed it. FR-09: append-only.
--
-- Not admin_audit_log: that table is about administering users (its actor and target are users
-- and its actions are a fixed list). This one is about a ticket, and its summary is written in
-- words at the time, so it still reads correctly after a technician is renamed or deactivated.
CREATE TABLE incident_events (
    id            UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    incident_id   UUID         NOT NULL REFERENCES incidents (id),
    event_type    VARCHAR(32)  NOT NULL,
    -- Who did it. NULL when the system did: a Freshdesk sync or a lapsing dispatch.
    actor_user_id UUID         REFERENCES users (id),
    dispatch_id   UUID         REFERENCES dispatches (id),
    summary       VARCHAR(500) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT incident_events_type_check CHECK (event_type IN (
        'INCIDENT_LOGGED', 'INCIDENT_RECEIVED', 'DISPATCH_SENT', 'DISPATCH_RESENT',
        'DISPATCH_ACCEPTED', 'DISPATCH_DECLINED', 'DISPATCH_EXPIRED', 'INCIDENT_CLOSED'
    ))
);

CREATE INDEX incident_events_incident_idx ON incident_events (incident_id, created_at);

-- What happened before this table existed, rebuilt from the rows that were kept. Who logged an
-- incident, and who was nudged and when, were never stored, so those are left out rather than
-- guessed.
INSERT INTO incident_events (incident_id, event_type, summary, created_at)
SELECT i.id,
       CASE WHEN i.freshdesk_ticket_id IS NULL THEN 'INCIDENT_LOGGED' ELSE 'INCIDENT_RECEIVED' END,
       CASE WHEN i.freshdesk_ticket_id IS NULL THEN 'Logged by a controller'
            ELSE 'Received from Freshdesk (' || i.freshdesk_ticket_id || ')' END,
       i.created_at
FROM incidents i;

INSERT INTO incident_events (incident_id, event_type, actor_user_id, dispatch_id, summary, created_at)
SELECT d.incident_id, 'DISPATCH_SENT', d.created_by, d.id,
       CASE WHEN d.dispatch_type = 'ASSIGN' THEN 'Assigned to ' ELSE 'Broadcast to ' END
           || COALESCE((SELECT string_agg(u.name, ', ' ORDER BY u.name)
                        FROM dispatch_responses r JOIN users u ON u.id = r.technician_id
                        WHERE r.dispatch_id = d.id), 'no one'),
       d.created_at
FROM dispatches d;

INSERT INTO incident_events (incident_id, event_type, actor_user_id, dispatch_id, summary, created_at)
SELECT d.incident_id,
       CASE r.response WHEN 'ACCEPTED' THEN 'DISPATCH_ACCEPTED' ELSE 'DISPATCH_DECLINED' END,
       r.technician_id, d.id,
       CASE r.response WHEN 'ACCEPTED' THEN 'Accepted the job' ELSE 'Declined the job' END,
       r.responded_at
FROM dispatch_responses r JOIN dispatches d ON d.id = r.dispatch_id
WHERE r.response IN ('ACCEPTED', 'DECLINED') AND r.responded_at IS NOT NULL;

INSERT INTO incident_events (incident_id, event_type, dispatch_id, summary, created_at)
SELECT d.incident_id, 'DISPATCH_EXPIRED', d.id, 'Nobody accepted in time', d.expires_at
FROM dispatches d
WHERE d.status = 'EXPIRED';

INSERT INTO incident_events (incident_id, event_type, summary, created_at)
SELECT i.id, 'INCIDENT_CLOSED', 'Closed', i.closed_at
FROM incidents i
WHERE i.closed_at IS NOT NULL;

-- FR-09: "shall not be editable or deletable by any user". The repository has no update or
-- delete method; this makes the database refuse them too, whoever is connected.
CREATE FUNCTION incident_events_append_only() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'incident_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER incident_events_append_only
    BEFORE UPDATE OR DELETE ON incident_events
    FOR EACH ROW EXECUTE FUNCTION incident_events_append_only();
