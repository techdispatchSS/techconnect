package silver.solutions.techconnect.entity;

public enum UserStatus {
    /** Created by a Manager but has never set a password. Cannot log in. */
    PENDING_ACTIVATION,
    ACTIVE,
    /** Offboarded (M-06). A soft-disable — user rows are never deleted. */
    DISABLED
}
