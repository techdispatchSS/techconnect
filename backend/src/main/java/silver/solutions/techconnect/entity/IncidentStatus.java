package silver.solutions.techconnect.entity;

/** PRD FR-02/FR-03 — an incident's place in the controller's queue. */
public enum IncidentStatus {
    NEW,
    IN_PROGRESS,
    ON_HOLD,
    OVERDUE,
    CLOSED
}
