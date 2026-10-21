package silver.solutions.techconnect.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.controller.DispatchResponseEntry;
import silver.solutions.techconnect.dto.response.controller.DispatchSummary;
import silver.solutions.techconnect.dto.response.controller.IncidentLock;
import silver.solutions.techconnect.dto.response.controller.IncidentProgressResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.Job;

@Component
public class IncidentMapper {

    /**
     * @param latestDispatch the incident's most recent dispatch, or null if none was sent
     * @param responses      every invitee's answer to that dispatch (empty if none)
     * @param technicianNamesById names for at least the technicians who accepted
     */
    public IncidentResponse toIncidentResponse(
            Incident incident,
            Dispatch latestDispatch,
            List<DispatchResponse> responses,
            Map<UUID, String> technicianNamesById) {
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
                latestDispatch != null,
                latestDispatch == null ? null : summarise(latestDispatch, responses, technicianNamesById),
                lockOf(incident, technicianNamesById));
    }

    /** The live dispatch lock, named from {@code namesById} (which carries the holder's name). */
    private static IncidentLock lockOf(Incident incident, Map<UUID, String> namesById) {
        UUID holder = incident.liveLockHolder(java.time.Instant.now());
        return holder == null
                ? null
                : new IncidentLock(holder, namesById.getOrDefault(holder, "Another controller"),
                        incident.getLockExpiresAt());
    }

    /** "Sent to 3 technicians, 1 has declined, 2 are still to answer." */
    public DispatchSummary summarise(
            Dispatch dispatch, List<DispatchResponse> responses, Map<UUID, String> namesById) {
        int pending = 0;
        int declined = 0;
        int noResponse = 0;
        String acceptedName = null;
        for (DispatchResponse response : responses) {
            switch (response.getResponse()) {
                case PENDING -> pending++;
                case DECLINED -> declined++;
                case NO_RESPONSE -> noResponse++;
                case ACCEPTED -> acceptedName =
                        namesById.getOrDefault(response.getTechnicianId(), "Unknown technician");
            }
        }
        return new DispatchSummary(
                dispatch.getId(),
                dispatch.getDispatchType(),
                dispatch.getStatus(),
                dispatch.getCreatedAt(),
                dispatch.getExpiresAt(),
                responses.size(),
                pending,
                declined,
                noResponse,
                acceptedName,
                dispatch.getNotificationCount(),
                dispatch.getLastNotifiedAt());
    }

    /**
     * The ticket progress view: dispatch method, required skills/certs, how every invited
     * technician has responded and, once accepted, the job that was opened for it.
     */
    public IncidentProgressResponse toProgressResponse(
            Incident incident,
            Dispatch dispatch,
            List<DispatchResponse> responses,
            Map<UUID, String> technicianNamesById,
            Job job) {

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
                toIncidentResponse(incident, dispatch, responses, technicianNamesById),
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
                acceptedTechnicianName,
                job == null ? null : job.getId(),
                job == null ? null : job.getStatus());
    }

    /** Falls back to a short, stable reference for incidents a Controller logged directly
     * (no Freshdesk ticket) rather than showing a raw UUID in the queue. */
    public static String ticketRef(Incident incident) {
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
