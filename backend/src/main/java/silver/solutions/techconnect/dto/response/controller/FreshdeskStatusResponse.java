package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;

/**
 * How the Freshdesk connection stands, so the dashboard header can say so truthfully.
 *
 * @param mode       {@code SIMULATED} or {@code NOT_CONNECTED}
 * @param simulation whether the simulation controls (raise a ticket, answer as a technician)
 *                   are available
 */
public record FreshdeskStatusResponse(
        String mode,
        boolean simulation,
        Instant lastSyncAt,
        Instant lastSuccessAt,
        String lastError) {
}
