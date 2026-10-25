package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.controller.CreateIncidentRequest;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.dto.response.controller.FreshdeskUpdateResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentEventResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentKpiResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentProgressResponse;
import silver.solutions.techconnect.dto.response.controller.IncidentResponse;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.dto.response.controller.IncidentLock;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskUpdateService;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.IncidentEventService;
import silver.solutions.techconnect.service.IncidentLockService;
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
    private final IncidentLockService locks;
    private final FreshdeskUpdateService freshdesk;
    private final IncidentEventService events;

    public IncidentController(
            IncidentService incidentService,
            IncidentLockService locks,
            FreshdeskUpdateService freshdesk,
            IncidentEventService events) {
        this.incidentService = incidentService;
        this.locks = locks;
        this.freshdesk = freshdesk;
        this.events = events;
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

    /** A Controller logging a fault they took by phone; it joins the queue as NEW. */
    @PostMapping
    public ResponseEntity<IncidentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateIncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.create(principal.id(), request));
    }

    @GetMapping("/kpis")
    public IncidentKpiResponse kpis() {
        return incidentService.kpis();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable UUID id) {
        return incidentService.get(id);
    }

    /**
     * Takes (or renews) the hold on dispatching this incident. The dispatch screen calls it when it
     * opens and again every so often; 409 names whoever else is dispatching it.
     */
    @PostMapping("/{id}/lock")
    public IncidentLock lock(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return locks.acquire(id, principal.id());
    }

    /** Lets go of the hold; harmless if this controller does not have it. */
    @DeleteMapping("/{id}/lock")
    public ResponseEntity<Void> unlock(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        locks.release(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    /** The dispatch that was sent and how every invited technician has responded so far. */
    @GetMapping("/{id}/progress")
    public IncidentProgressResponse progress(@PathVariable UUID id) {
        return incidentService.progress(id);
    }

    /**
     * What the incident's Freshdesk ticket has been told (or, in demo mode, would have been told),
     * oldest first. Empty for an incident with no ticket.
     */
    @GetMapping("/{id}/freshdesk-updates")
    public List<FreshdeskUpdateResponse> freshdeskUpdates(@PathVariable UUID id) {
        incidentService.get(id);
        return freshdesk.forIncident(id).stream().map(FreshdeskUpdateResponse::of).toList();
    }

    /** The ticket's history, oldest first: who logged, dispatched, nudged, answered and closed it. */
    @GetMapping("/{id}/events")
    public List<IncidentEventResponse> events(@PathVariable UUID id) {
        return events.timeline(id);
    }
}
