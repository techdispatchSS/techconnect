package silver.solutions.techdispatch.domain;

/**
 * Administrative actions recorded in {@code admin_audit_log}. FR-09 mandates an audit
 * trail for job status changes; this extends the same guarantee to "who granted whom
 * access", which is what makes M-06 defensible.
 */
public enum AdminAuditAction {
    USER_CREATED,
    USER_UPDATED,
    USER_ROLE_CHANGED,
    USER_DEACTIVATED,
    USER_REACTIVATED,
    INVITE_RESENT
}
