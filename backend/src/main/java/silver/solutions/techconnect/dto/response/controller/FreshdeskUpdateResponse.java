package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import silver.solutions.techconnect.entity.FreshdeskUpdate;

/**
 * One update for an incident's Freshdesk ticket, as the ticket page lists them.
 *
 * @param action   {@code NOTE} (a private note) or {@code RESOLVE} (ticket set to resolved)
 * @param body     the note's text; null for a resolve
 * @param delivery {@code DEMO} (recorded only, never sent), {@code PENDING}, {@code SENT} or
 *                 {@code FAILED}
 */
public record FreshdeskUpdateResponse(
        long id,
        String event,
        String action,
        String body,
        String delivery,
        String lastError,
        Instant createdAt,
        Instant sentAt) {

    public static FreshdeskUpdateResponse of(FreshdeskUpdate update) {
        return new FreshdeskUpdateResponse(
                update.getId(),
                update.getEvent().name(),
                update.getAction().name(),
                update.getBody(),
                update.getDelivery().name(),
                update.getLastError(),
                update.getCreatedAt(),
                update.getSentAt());
    }
}
