package silver.solutions.techconnect.controller;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.response.controller.DispatchAnswerResponse;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.DispatchLifecycleService;

/**
 * A technician's answer to a dispatch they were invited to (FR-04). TECHNICIAN-only — enforced
 * centrally by the {@code /v1/technician/**} rule in {@code SecurityConfig}. The technician is
 * always the authenticated caller, never a value in the request, so nobody can answer on
 * someone else's behalf.
 */
@RestController
@RequestMapping("/v1/technician/dispatches")
public class TechnicianDispatchController {

    private final DispatchLifecycleService lifecycle;

    public TechnicianDispatchController(DispatchLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/{id}/accept")
    public DispatchAnswerResponse accept(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return DispatchLifecycleService.toResponse(id, lifecycle.respond(principal.id(), id, true));
    }

    @PostMapping("/{id}/decline")
    public DispatchAnswerResponse decline(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return DispatchLifecycleService.toResponse(id, lifecycle.respond(principal.id(), id, false));
    }
}
