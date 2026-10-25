package silver.solutions.techconnect.integration.freshdesk;

import java.time.Instant;
import java.util.Map;

/**
 * The slice of a Freshdesk ticket (API v2 "ticket" with requester and company included) that
 * TechConnect needs to raise an incident. Field meanings follow Freshdesk's own: priority is
 * 1 low / 2 medium / 3 high / 4 urgent, status is 2 open / 3 pending / 4 resolved / 5 closed.
 * Accounts add their own statuses on top (the owners' has 6 waiting on customer, 7 waiting on
 * third party, 9000 assigned to AI agent); anything but resolved or closed counts as still open.
 *
 * <p>Freshdesk has no native site address or coordinates; where they live is a per-account
 * custom-field choice (see {@code FreshdeskIncidentMapper}), which is one of the things the
 * owners have to confirm before a live connection is built.
 */
public record FreshdeskTicket(
        String id,
        String subject,
        String descriptionText,
        /** Freshdesk's ticket "type" (e.g. Incident, Problem); may be null. */
        String type,
        int priority,
        int status,
        String companyName,
        String requesterName,
        String requesterPhone,
        /** Freshdesk's resolution due date: the SLA clock. */
        Instant dueBy,
        Instant createdAt,
        Instant updatedAt,
        /** Account-specific {@code cf_*} fields, keyed as Freshdesk names them. */
        Map<String, String> customFields) {

    public static final int STATUS_OPEN = 2;
    public static final int STATUS_PENDING = 3;
    public static final int STATUS_RESOLVED = 4;
    public static final int STATUS_CLOSED = 5;

    public boolean isResolved() {
        return status == STATUS_RESOLVED || status == STATUS_CLOSED;
    }

    public String customField(String key) {
        return customFields == null ? null : customFields.get(key);
    }
}
