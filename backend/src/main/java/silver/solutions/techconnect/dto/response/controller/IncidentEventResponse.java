package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.IncidentEventType;

/** One line of a ticket's history. {@code actorName} is null when the system did it. */
public record IncidentEventResponse(
        UUID id, IncidentEventType type, String actorName, String summary, Instant at) {
}
