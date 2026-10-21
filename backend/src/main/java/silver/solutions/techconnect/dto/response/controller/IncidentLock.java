package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.UUID;

/** Who is dispatching an incident right now, and until when their hold lasts without a refresh. */
public record IncidentLock(UUID byId, String byName, Instant expiresAt) {
}
