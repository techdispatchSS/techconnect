package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.DispatchType;

/**
 * Where an incident's latest dispatch stands, for the queue and ticket: who it went to, who has
 * answered, and whether anyone has taken it. The raw material for "Sent to 3 technicians —
 * awaiting confirmation".
 *
 * @param invited     technicians the dispatch was sent to
 * @param pending     of those, how many have not answered (and still can, while PENDING)
 * @param declined    how many said no
 * @param noResponse  how many never answered before it lapsed or was taken by someone else
 * @param notificationCount how many times invitees have been notified (first send included)
 */
public record DispatchSummary(
        UUID id,
        DispatchType type,
        DispatchStatus status,
        Instant sentAt,
        Instant expiresAt,
        int invited,
        int pending,
        int declined,
        int noResponse,
        String acceptedTechnicianName,
        int notificationCount,
        Instant lastNotifiedAt) {
}
