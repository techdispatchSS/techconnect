-- The controller dashboard's job flow (Active jobs, Review & close) and reports.

-- When an incident was closed. Reports need it to say whether the SLA was met; until now the
-- only trace was updated_at, which any later edit would have moved.
ALTER TABLE incidents ADD COLUMN closed_at TIMESTAMPTZ;
UPDATE incidents SET closed_at = updated_at WHERE status = 'CLOSED';

-- One job per dispatch. A retried accept must never open a second job for the same work.
CREATE UNIQUE INDEX jobs_dispatch_unique_idx ON jobs (dispatch_id) WHERE dispatch_id IS NOT NULL;

-- Dispatches accepted before jobs were opened on accept have no job to follow. Give each one,
-- starting at EN_ROUTE, so they appear in Active jobs rather than silently missing.
INSERT INTO jobs (incident_id, dispatch_id, technician_id, status, created_at, updated_at)
SELECT d.incident_id,
       d.id,
       r.technician_id,
       'EN_ROUTE',
       COALESCE(r.responded_at, d.created_at),
       COALESCE(r.responded_at, d.created_at)
FROM dispatches d
         JOIN dispatch_responses r ON r.dispatch_id = d.id AND r.response = 'ACCEPTED'
WHERE d.status = 'ACCEPTED'
  AND NOT EXISTS (SELECT 1 FROM jobs j WHERE j.dispatch_id = d.id);

INSERT INTO job_status_history (job_id, status, previous_status, reason, changed_by, changed_at)
SELECT j.id, 'EN_ROUTE', NULL, 'Dispatch accepted', j.technician_id, j.created_at
FROM jobs j
WHERE NOT EXISTS (SELECT 1 FROM job_status_history h WHERE h.job_id = j.id);

CREATE INDEX incidents_closed_at_idx ON incidents (closed_at) WHERE closed_at IS NOT NULL;
CREATE INDEX dispatches_created_at_idx ON dispatches (created_at);
