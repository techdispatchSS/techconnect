-- A technician who already has a job in hand is on a job. Jobs opened by V12 for dispatches
-- accepted earlier did not move the technician's availability the way a new accept now does.
-- Left alone: anyone who has set themselves offline.
UPDATE technician_profiles
SET status = 'ON_JOB',
    updated_at = now()
WHERE status = 'AVAILABLE'
  AND user_id IN (SELECT technician_id
                  FROM jobs
                  WHERE status IN ('EN_ROUTE', 'ON_SITE', 'WORK_STARTED', 'ON_HOLD', 'WORK_RESUMED'));
