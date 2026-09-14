package silver.solutions.techconnect.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.controller.IncidentKpiResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.IncidentMapper;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.IncidentSpecifications;

/**
 * The controller dashboard's incident queue (PRD FR-02, FR-03). Access is restricted to the
 * CONTROLLER role by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final IncidentMapper incidentMapper;

    public IncidentService(
            IncidentRepository incidents, DispatchRepository dispatches, IncidentMapper incidentMapper) {
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.incidentMapper = incidentMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(
            IncidentStatus status, IncidentPriority priority, String query, boolean unassignedOnly,
            Pageable pageable) {

        Page<Incident> page = incidents.findAll(
                IncidentSpecifications.matching(status, priority, query, unassignedOnly), pageable);

        // One query for every dispatch on the page, rather than one per row.
        Map<UUID, Dispatch> latestByIncident = latestDispatchesFor(page);

        return PageResponse.of(page, incident ->
                incidentMapper.toIncidentResponse(incident, latestByIncident.get(incident.getId())));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID id) {
        Incident incident = require(id);
        Dispatch latest = dispatches.findByIncidentIdOrderByCreatedAtDesc(id)
                .stream().findFirst().orElse(null);
        return incidentMapper.toIncidentResponse(incident, latest);
    }

    @Transactional(readOnly = true)
    public IncidentKpiResponse kpis() {
        return new IncidentKpiResponse(
                incidents.countByStatus(IncidentStatus.NEW),
                incidents.countUnassigned(IncidentStatus.CLOSED, DispatchStatus.EXPIRED),
                incidents.countByStatus(IncidentStatus.IN_PROGRESS),
                incidents.countByStatus(IncidentStatus.OVERDUE));
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
