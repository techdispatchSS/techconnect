package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.technician.DispatchOfferResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.mapper.DispatchMapper;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;

/**
 * The technician's side of a dispatch before they answer it (FR-04): the offers waiting on
 * them. Answering goes through {@link DispatchLifecycleService}.
 */
@Service
public class DispatchOfferService {

    private final DispatchRepository dispatches;
    private final IncidentRepository incidents;
    private final TechnicianProfileRepository profiles;

    public DispatchOfferService(
            DispatchRepository dispatches,
            IncidentRepository incidents,
            TechnicianProfileRepository profiles) {
        this.dispatches = dispatches;
        this.incidents = incidents;
        this.profiles = profiles;
    }

    /** Open offers still inside their window that this technician hasn't answered, soonest to lapse first. */
    @Transactional(readOnly = true)
    public List<DispatchOfferResponse> awaitingAnswer(UUID technicianId) {
        List<Dispatch> open = dispatches.findAwaitingAnswerFrom(
                technicianId, DispatchResponseStatus.PENDING, DispatchStatus.PENDING, Instant.now());
        if (open.isEmpty()) {
            return List.of();
        }
        Map<UUID, Incident> byId = incidents.findAllById(open.stream().map(Dispatch::getIncidentId).toList())
                .stream().collect(Collectors.toMap(Incident::getId, Function.identity()));
        TechnicianProfile me = profiles.findById(technicianId).orElse(null);

        return open.stream()
                .filter(d -> byId.containsKey(d.getIncidentId()))
                .map(d -> toOffer(d, byId.get(d.getIncidentId()), me))
                .toList();
    }

    private static DispatchOfferResponse toOffer(Dispatch dispatch, Incident incident, TechnicianProfile me) {
        Double distanceKm = me == null ? null : DispatchMapper.distanceBetween(
                me.getLastLatitude(), me.getLastLongitude(),
                incident.getSiteLatitude(), incident.getSiteLongitude());
        return new DispatchOfferResponse(
                dispatch.getId(),
                dispatch.getDispatchType(),
                dispatch.getExpiresAt(),
                incident.getFreshdeskTicketId(),
                incident.getClientName(),
                incident.getSiteAddress(),
                incident.getPriority(),
                dispatch.getSlaResponse() != null ? dispatch.getSlaResponse() : incident.getSla(),
                incident.getIssueType(),
                incident.getDescription(),
                dispatch.getJobType(),
                dispatch.getRequiredSkills(),
                dispatch.getNotesForTechnician(),
                distanceKm);
    }
}
