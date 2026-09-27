package silver.solutions.techconnect.entity;

/**
 * PRD FR-05/FR-06/FR-07 — a job's on-site lifecycle, from the technician setting out to the
 * work being closed off. {@link #ON_HOLD} / {@link #WORK_RESUMED} are a pair rather than a
 * single toggle so {@link JobStatusHistory} keeps recording a distinct, orderable status even
 * across repeated pauses.
 */
public enum JobStatus {
    EN_ROUTE,
    ON_SITE,
    WORK_STARTED,
    ON_HOLD,
    WORK_RESUMED,
    COMPLETE,
    SUBMITTED,
    CLOSED
}
