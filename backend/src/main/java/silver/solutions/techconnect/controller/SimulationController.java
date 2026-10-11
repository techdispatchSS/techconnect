package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.controller.SimulateTechnicianResponseRequest;
import silver.solutions.techconnect.dto.response.controller.DispatchAnswerResponse;
import silver.solutions.techconnect.dto.response.controller.SimulatedTicketResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskSyncService;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskTicket;
import silver.solutions.techconnect.integration.freshdesk.SimulatedFreshdeskClient;
import silver.solutions.techconnect.service.DispatchLifecycleService;

/**
 * Stand-ins for the two parties that are not connected yet, so the dashboard can be exercised
 * end to end during user testing. The whole controller does not exist unless
 * {@code techconnect.simulation.enabled} is true: with it off these routes are a plain 404, not
 * merely forbidden. CONTROLLER-only — enforced centrally by the {@code /v1/controller/**} rule.
 *
 * <p>Both go through the real code paths: a raised ticket reaches the queue via the same sync a
 * live Freshdesk would use, and a simulated answer goes through the same lifecycle service as a
 * technician's own accept.
 */
@RestController
@RequestMapping("/v1/controller/simulation")
@ConditionalOnProperty(name = "techconnect.simulation.enabled", havingValue = "true")
public class SimulationController {

    private final SimulatedFreshdeskClient freshdesk;
    private final FreshdeskSyncService sync;
    private final DispatchLifecycleService lifecycle;

    public SimulationController(
            SimulatedFreshdeskClient freshdesk,
            FreshdeskSyncService sync,
            DispatchLifecycleService lifecycle) {
        this.freshdesk = freshdesk;
        this.sync = sync;
        this.lifecycle = lifecycle;
    }

    /** Raises a ticket in the simulated Freshdesk and syncs, so it is in the queue on return. */
    @PostMapping("/freshdesk-tickets")
    public SimulatedTicketResponse raiseTicket(@RequestParam(defaultValue = "false") boolean overdue) {
        FreshdeskTicket ticket = freshdesk.raise(overdue);
        FreshdeskSyncService.Result result = sync.sync();
        return new SimulatedTicketResponse(ticket.id(), result.created(), result.updated());
    }

    /** Answers a dispatch as one of the technicians it was sent to. */
    @PostMapping("/dispatches/{dispatchId}/respond")
    public DispatchAnswerResponse respond(
            @PathVariable UUID dispatchId, @Valid @RequestBody SimulateTechnicianResponseRequest request) {
        boolean accept = switch (request.response()) {
            case ACCEPTED -> true;
            case DECLINED -> false;
            default -> throw ApiException.badRequest("Respond with ACCEPTED or DECLINED.");
        };
        return DispatchLifecycleService.toResponse(
                dispatchId, lifecycle.respond(request.technicianId(), dispatchId, accept));
    }
}
