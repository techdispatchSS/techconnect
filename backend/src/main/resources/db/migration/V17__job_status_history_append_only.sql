-- FR-09 / §9.5: job status changes "shall not be editable or deletable by any user". The
-- repository has no update or delete method; this makes the database refuse them too, whoever is
-- connected, the same as incident_events (V15). TRUNCATE is refused as well, since it would
-- otherwise skip the row-level trigger. Retention is in docs/operations/data-retention.md.
CREATE FUNCTION job_status_history_append_only() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'job_status_history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER job_status_history_append_only
    BEFORE UPDATE OR DELETE ON job_status_history
    FOR EACH ROW EXECUTE FUNCTION job_status_history_append_only();

CREATE TRIGGER job_status_history_no_truncate
    BEFORE TRUNCATE ON job_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION job_status_history_append_only();
