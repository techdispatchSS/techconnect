package silver.solutions.techconnect.entity;

/** The kinds of thing recorded in a ticket's history ({@code incident_events}, FR-09). */
public enum IncidentEventType {
    INCIDENT_LOGGED,
    INCIDENT_RECEIVED,
    DISPATCH_SENT,
    DISPATCH_RESENT,
    DISPATCH_ACCEPTED,
    DISPATCH_DECLINED,
    DISPATCH_EXPIRED,
    INCIDENT_CLOSED
}
