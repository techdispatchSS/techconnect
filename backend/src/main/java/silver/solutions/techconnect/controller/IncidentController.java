package silver.solutions.techconnect.controller;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentKpiResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.service.IncidentService;

/**
 * Controller dashboard incident-queue endpoints (FR-02, FR-03). CONTROLLER-only — enforced
 * centrally by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/controller/incidents")
public class IncidentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /** Filtering and paging happen in the database, not the browser. */
    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentPriority priority,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "slaDueAt").and(Sort.by(Sort.Direction.DESC, "createdAt")));

        return incidentService.list(status, priority, q, unassigned, pageable);
    }

    @GetMapping("/kpis")
    public IncidentKpiResponse kpis() {
        return incidentService.kpis();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable UUID id) {
        return incidentService.get(id);
    }
}
