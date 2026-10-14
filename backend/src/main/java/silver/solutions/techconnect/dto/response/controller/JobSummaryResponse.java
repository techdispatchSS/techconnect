package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.JobStatus;

/** One job as a row in Active jobs or Review &amp; close: who is on what, and where it stands. */
public record JobSummaryResponse(
        UUID id,
        UUID incidentId,
        String ticketRef,
        String clientName,
        String siteAddress,
        IncidentPriority priority,
        Instant slaDueAt,
        boolean overdue,
        UUID technicianId,
        String technicianName,
        JobStatus status,
        Instant acceptedAt,
        Instant arrivedAt,
        Instant startedAt,
        Instant completedAt,
        Instant submittedAt,
        Instant closedAt) {
}
