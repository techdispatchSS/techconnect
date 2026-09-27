package silver.solutions.techconnect.entity;

/** PRD FR-04 — a dispatch's own lifecycle, independent of any one technician's response. */
public enum DispatchStatus {
    PENDING,
    ACCEPTED,
    /** The 10-minute response window (see {@code DispatchService}) passed with no acceptance. */
    EXPIRED
}
