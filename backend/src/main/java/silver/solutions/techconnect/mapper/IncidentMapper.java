package silver.solutions.techconnect.mapper;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.Incident;

@Component
public class IncidentMapper {

    public IncidentResponse toIncidentResponse(Incident incident, Dispatch latestDispatch) {
        return new IncidentResponse(
                incident.getId(),
                ticketRef(incident),
                incident.getClientName(),
                incident.getSiteAddress(),
                incident.getSiteLatitude() == null ? null : incident.getSiteLatitude().doubleValue(),
                incident.getSiteLongitude() == null ? null : incident.getSiteLongitude().doubleValue(),
                incident.getIssueType(),
                incident.getPriority(),
                incident.getSla(),
                incident.getStatus(),
                incident.isOverdue(),
                incident.getContactName(),
                incident.getContactPhone(),
                incident.getDescription(),
                incident.getAdditionalNotes(),
                incident.getSlaDueAt(),
                incident.getCreatedAt(),
                latestDispatch == null ? List.of() : splitCsv(latestDispatch.getRequiredSkills()));
    }

    /** Falls back to a short, stable reference for incidents a Controller logged directly
     * (no Freshdesk ticket) rather than showing a raw UUID in the queue. */
    private String ticketRef(Incident incident) {
        if (incident.getFreshdeskTicketId() != null && !incident.getFreshdeskTicketId().isBlank()) {
            return incident.getFreshdeskTicketId();
        }
        return "INC-" + incident.getId().toString().substring(0, 8).toUpperCase();
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
