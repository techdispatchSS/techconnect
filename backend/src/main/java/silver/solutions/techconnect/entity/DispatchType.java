package silver.solutions.techconnect.entity;

/** PRD FR-04 — how a dispatch reaches technicians. */
public enum DispatchType {
    /** Fanned out to every selected technician; the first ACCEPT wins the job. */
    BROADCAST,
    /** Sent to exactly one technician, skipping the response race. */
    ASSIGN
}
