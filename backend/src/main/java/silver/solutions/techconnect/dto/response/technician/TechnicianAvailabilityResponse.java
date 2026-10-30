package silver.solutions.techconnect.dto.response.technician;

import java.math.BigDecimal;
import java.time.Instant;
import silver.solutions.techconnect.entity.TechnicianStatus;

/**
 * The technician's own availability, as the controller sees it when dispatching (PRD §8.5).
 * {@code onJob} is true while they hold a job that is not yet done, which is also when they
 * can't change their status themselves. The location fields are null until a first report.
 */
public record TechnicianAvailabilityResponse(
        TechnicianStatus status,
        boolean onJob,
        BigDecimal lastLatitude,
        BigDecimal lastLongitude,
        Instant lastLocationAt) {
}
