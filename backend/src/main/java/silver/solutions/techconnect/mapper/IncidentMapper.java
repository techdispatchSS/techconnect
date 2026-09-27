package silver.solutions.techconnect.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.controller.DispatchResponseEntry;
import silver.solutions.techconnect.dto.response.controller.IncidentProgressResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
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
                latestDispatch == null ? List.of() : splitCsv(latestDispatch.getRequiredSkills()),
                latestDispatch != null);
    }

    /**
     * The ticket progress view (dispatch method, required skills/certs, and how every invited
     * technician has responded) — see {@link IncidentProgressResponse} for why there is no
     * on-site job timeline here yet.
     */
    public IncidentProgressResponse toProgressResponse(
            Incident incident,
            Dispatch dispatch,
            List<DispatchResponse> responses,
            Map<UUID, String> technicianNamesById) {

        List<DispatchResponseEntry> entries = responses.stream()
                .map(r -> new DispatchResponseEntry(
                        r.getTechnicianId(),
                        technicianNamesById.getOrDefault(r.getTechnicianId(), "Unknown technician"),
                        r.getResponse(),
                        r.getRespondedAt()))
                .toList();

        String acceptedTechnicianName = entries.stream()
                .filter(entry -> entry.response() == DispatchResponseStatus.ACCEPTED)
                .map(DispatchResponseEntry::technicianName)
                .findFirst()
                .orElse(null);

        return new IncidentProgressResponse(
                toIncidentResponse(incident, dispatch),
                dispatch.getId(),
                dispatch.getDispatchType(),
                dispatch.getStatus(),
                dispatch.getJobType(),
                splitCsv(dispatch.getRequiredSkills()),
                splitCsv(dispatch.getRequiredCertifications()),
                dispatch.getSlaResponse(),
                dispatch.getSiteContact(),
                dispatch.getNotesForTechnician(),
                dispatch.getCreatedAt(),
                dispatch.getExpiresAt(),
                entries,
                acceptedTechnicianName);
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
