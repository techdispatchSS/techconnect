package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;

/**
 * How the Freshdesk connection stands, so the dashboard header can say so truthfully.
 *
 * @param mode       {@code LIVE}, {@code SIMULATED} or {@code NOT_CONNECTED}
 * @param simulation whether the simulation controls (raise a ticket, answer as a technician)
 *                   are available
 * @param pushMode   what happens to updates for Freshdesk tickets: {@code OFF}, {@code DEMO}
 *                   (recorded and shown, never sent) or {@code LIVE} (sent)
 */
public record FreshdeskStatusResponse(
        String mode,
        boolean simulation,
        Instant lastSyncAt,
        Instant lastSuccessAt,
        String lastError,
        String pushMode) {
}
