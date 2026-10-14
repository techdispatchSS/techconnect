package silver.solutions.techconnect.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.request.controller.CreateIncidentRequest;
import silver.solutions.techconnect.dto.response.controller.IncidentKpiResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentProgressResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.IncidentMapper;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.DispatchResponseRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.IncidentSpecifications;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.util.Strings;

/**
 * The controller dashboard's incident queue (PRD FR-02, FR-03). Access is restricted to the
 * CONTROLLER role by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final DispatchResponseRepository dispatchResponses;
    private final UserRepository users;
    private final IncidentMapper incidentMapper;
    private final JobRepository jobs;

    public IncidentService(
            IncidentRepository incidents,
            DispatchRepository dispatches,
            DispatchResponseRepository dispatchResponses,
            UserRepository users,
            IncidentMapper incidentMapper,
            JobRepository jobs) {
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.dispatchResponses = dispatchResponses;
        this.users = users;
        this.incidentMapper = incidentMapper;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(
            IncidentStatus status, IncidentPriority priority, String query, boolean unassignedOnly,
            Pageable pageable) {

        Page<Incident> page = incidents.findAll(
                IncidentSpecifications.matching(status, priority, query, unassignedOnly), pageable);

        // One query for every dispatch on the page, rather than one per row.
        Map<UUID, Dispatch> latestByIncident = latestDispatchesFor(page);

        Map<UUID, List<DispatchResponse>> responsesByDispatch = responsesFor(latestByIncident.values());
        Map<UUID, String> acceptedNames = acceptedNames(responsesByDispatch);

        return PageResponse.of(page, incident -> {
            Dispatch latest = latestByIncident.get(incident.getId());
            List<DispatchResponse> responses = latest == null
                    ? List.of()
                    : responsesByDispatch.getOrDefault(latest.getId(), List.of());
            return incidentMapper.toIncidentResponse(incident, latest, responses, acceptedNames);
        });
    }

    /**
     * Logs an incident a Controller took by phone. It enters the queue as NEW exactly like one
     * that arrives from Freshdesk, with no ticket id (it shows as {@code INC-xxxxxxxx}).
     */
    @Transactional
    public IncidentResponse create(CreateIncidentRequest request) {
        if ((request.siteLatitude() == null) != (request.siteLongitude() == null)) {
            throw ApiException.badRequest("Provide both a latitude and a longitude, or neither.");
        }
        String clientName = Strings.blankToNull(Strings.cleanText(request.clientName()));
        if (clientName == null) {
            throw ApiException.badRequest("Client name is required.");
        }

        int slaHours = request.slaHours() != null
                ? request.slaHours()
                : defaultSlaHours(request.priority());

        Incident incident = new Incident();
        incident.setClientName(clientName);
        incident.setSiteAddress(clean(request.siteAddress()));
        incident.setSiteLatitude(request.siteLatitude());
        incident.setSiteLongitude(request.siteLongitude());
        incident.setContactName(clean(request.contactName()));
        incident.setContactPhone(clean(request.contactPhone()));
        incident.setIssueType(clean(request.issueType()));
        incident.setPriority(request.priority());
        incident.setDescription(clean(request.description()));
        incident.setAdditionalNotes(clean(request.additionalNotes()));
        incident.setSla(slaHours + (slaHours == 1 ? " hr" : " hrs"));
        incident.setSlaDueAt(incident.getCreatedAt().plus(Duration.ofHours(slaHours)));
        incident.setStatus(IncidentStatus.NEW);
        incidents.save(incident);

        return incidentMapper.toIncidentResponse(incident, null, List.of(), Map.of());
    }

    /** Response targets by priority, used when the Controller doesn't pick one. */
    private static int defaultSlaHours(IncidentPriority priority) {
        return switch (priority) {
            case HIGH -> 4;
            case MEDIUM -> 8;
            case LOW -> 24;
        };
    }

    private static String clean(String value) {
        return Strings.blankToNull(Strings.cleanText(value));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID id) {
        Incident incident = require(id);
        Dispatch latest = dispatches.findByIncidentIdOrderByCreatedAtDesc(id)
                .stream().findFirst().orElse(null);
        if (latest == null) {
            return incidentMapper.toIncidentResponse(incident, null, List.of(), Map.of());
        }
        Map<UUID, List<DispatchResponse>> responses = responsesFor(List.of(latest));
        return incidentMapper.toIncidentResponse(
                incident, latest, responses.getOrDefault(latest.getId(), List.of()),
                acceptedNames(responses));
    }

    /**
     * The ticket progress view (M-xx follow-up to FR-04): an in-progress incident's most
     * recent dispatch and every invited technician's response so far.
     */
    @Transactional(readOnly = true)
    public IncidentProgressResponse progress(UUID id) {
        Incident incident = require(id);
        Dispatch dispatch = dispatches.findByIncidentIdOrderByCreatedAtDesc(id).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("No dispatch has been created for this incident yet"));

        List<DispatchResponse> responses = dispatchResponses.findByDispatchId(dispatch.getId());
        List<UUID> technicianIds = responses.stream().map(DispatchResponse::getTechnicianId).toList();
        Map<UUID, String> namesById = users.findAllById(technicianIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return incidentMapper.toProgressResponse(
                incident, dispatch, responses, namesById, jobs.findByDispatchId(dispatch.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public IncidentKpiResponse kpis() {
        return new IncidentKpiResponse(
                incidents.countByStatus(IncidentStatus.NEW),
                incidents.countUnassigned(IncidentStatus.CLOSED, DispatchStatus.EXPIRED),
                incidents.countByStatus(IncidentStatus.IN_PROGRESS),
                incidents.countOverdue(IncidentStatus.CLOSED, IncidentStatus.OVERDUE, Instant.now()));
    }

    private Map<UUID, List<DispatchResponse>> responsesFor(java.util.Collection<Dispatch> dispatchList) {
        if (dispatchList.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = dispatchList.stream().map(Dispatch::getId).toList();
        return dispatchResponses.findByDispatchIdIn(ids).stream()
                .collect(Collectors.groupingBy(DispatchResponse::getDispatchId));
    }

    /** Names only for the technicians who accepted — that is all the summary shows by name. */
    private Map<UUID, String> acceptedNames(Map<UUID, List<DispatchResponse>> responsesByDispatch) {
        List<UUID> accepted = responsesByDispatch.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.getResponse() == DispatchResponseStatus.ACCEPTED)
                .map(DispatchResponse::getTechnicianId)
                .toList();
        if (accepted.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(accepted).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }

    private Map<UUID, Dispatch> latestDispatchesFor(Page<Incident> page) {
        List<UUID> incidentIds = page.getContent().stream().map(Incident::getId).toList();
        Map<UUID, Dispatch> latest = new HashMap<>();
        if (incidentIds.isEmpty()) {
            return latest;
        }
        for (Dispatch dispatch : dispatches.findByIncidentIdIn(incidentIds)) {
            latest.merge(dispatch.getIncidentId(), dispatch,
                    (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b);
        }
        return latest;
    }

    private Incident require(UUID id) {
        return incidents.findById(id).orElseThrow(() -> ApiException.notFound("Incident not found"));
    }
}
