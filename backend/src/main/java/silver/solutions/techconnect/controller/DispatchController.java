package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.controller.CreateDispatchRequest;
import silver.solutions.techconnect.dto.response.controller.DispatchCreateResponse;
import silver.solutions.techconnect.dto.response.controller.TechnicianCandidateResponse;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.DispatchService;

/**
 * Dispatch creation and technician ranking (FR-04). CONTROLLER-only — enforced centrally by
 * the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/controller")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /** Ranked by skill match, availability and proximity — see {@code DispatchMapper}. */
    @GetMapping("/incidents/{id}/technicians")
    public List<TechnicianCandidateResponse> recommend(
            @PathVariable UUID id,
            @RequestParam(required = false) List<String> skills) {
        return dispatchService.recommend(id, skills);
    }

    @PostMapping("/dispatches")
    public ResponseEntity<DispatchCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateDispatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dispatchService.create(principal.id(), request));
    }
}
