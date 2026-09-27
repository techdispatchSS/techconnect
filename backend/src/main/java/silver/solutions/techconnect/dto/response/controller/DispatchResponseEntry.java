package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchResponseStatus;

/** One invited technician's outcome within a dispatch, for the ticket progress view. */
public record DispatchResponseEntry(
        UUID technicianId,
        String technicianName,
        DispatchResponseStatus response,
        Instant respondedAt) {
}
