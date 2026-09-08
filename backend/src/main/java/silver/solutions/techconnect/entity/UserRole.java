package silver.solutions.techconnect.entity;

/**
 * PRD FR-01. There is deliberately no separate ADMIN role: the Manager holds the
 * administrator rights to onboard and offboard Controllers and Technicians (M-06).
 * "Dispatcher" in business conversation means {@link #CONTROLLER} here.
 */
public enum UserRole {
    CONTROLLER,
    TECHNICIAN,
    MANAGER
}
