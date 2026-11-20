package silver.solutions.techconnect.dto.response.common;

import java.util.UUID;

/**
 * A nudge over the socket that something changed, so a screen re-reads it at once rather than at
 * its next poll. Deliberately only ids and a status: the data itself is always fetched over REST,
 * where every permission check lives. {@code jobId} is null on a dispatch that has no job yet.
 */
public record RealtimeEvent(Type type, UUID incidentId, UUID dispatchId, UUID jobId, String status) {

    public enum Type {
        DISPATCH_CHANGED,
        JOB_CHANGED
    }
}
