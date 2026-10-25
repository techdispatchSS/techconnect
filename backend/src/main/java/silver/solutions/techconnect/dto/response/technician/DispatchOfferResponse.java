package silver.solutions.techconnect.dto.response.technician;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchType;
import silver.solutions.techconnect.entity.IncidentPriority;

/**
 * A job offered to the technician and waiting on their answer (FR-04): enough to decide
 * whether to take it. The site contact's name and number are left out until they accept.
 * {@code distanceKm} is from their last reported location, null if either end has none.
 */
public record DispatchOfferResponse(
        UUID dispatchId,
        DispatchType dispatchType,
        Instant expiresAt,
        String ticketRef,
        String clientName,
        String siteAddress,
        IncidentPriority priority,
        String sla,
        String issueType,
        String description,
        String jobType,
        String requiredSkills,
        String notesForTechnician,
        Double distanceKm) {
}
