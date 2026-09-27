package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;

public record IncidentResponse(
        UUID id,
        String ticketRef,
        String clientName,
        String siteAddress,
        Double siteLatitude,
        Double siteLongitude,
        String issueType,
        IncidentPriority priority,
        String sla,
        IncidentStatus status,
        boolean overdue,
        String contactName,
        String contactPhone,
        String description,
        String additionalNotes,
        Instant slaDueAt,
        Instant createdAt,
        List<String> requiredSkills,
        /** Whether any dispatch has ever been created for this incident. Status alone can't
         * say: an OVERDUE incident may or may not have one, and the dashboard needs to offer
         * "Create dispatch" versus "View progress" accordingly. */
        boolean hasDispatch) {
}
