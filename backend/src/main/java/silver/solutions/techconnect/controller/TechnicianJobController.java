package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.technician.UpdateJobStatusRequest;
import silver.solutions.techconnect.dto.response.controller.JobDetailResponse;
import silver.solutions.techconnect.dto.response.controller.JobSummaryResponse;
import silver.solutions.techconnect.dto.response.technician.MyJobResponse;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.JobService;

/**
 * A technician's own jobs and moving them along (FR-06, PRD §8.4). TECHNICIAN-only — enforced
 * centrally by the {@code /v1/technician/**} rule in {@code SecurityConfig}. Someone else's job
 * is a 404, the same as one that does not exist.
 */
@RestController
@RequestMapping("/v1/technician/jobs")
public class TechnicianJobController {

    private final JobService jobs;

    public TechnicianJobController(JobService jobs) {
        this.jobs = jobs;
    }

    /** Everything in hand, then the most recent finished ones. */
    @GetMapping
    public List<JobSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return jobs.forTechnician(principal.id());
    }

    @GetMapping("/{id}")
    public MyJobResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return mine(id, principal.id());
    }

    /**
     * One step on. Timestamps are the server's, never the client's. 409 for a step that isn't
     * allowed from where the job is; 400 for a hold without a reason.
     */
    @PutMapping("/{id}/status")
    public MyJobResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobStatusRequest request) {
        jobs.advance(id, principal.id(), request.status(), request.reason());
        return mine(id, principal.id());
    }

    private MyJobResponse mine(UUID jobId, UUID technicianId) {
        JobDetailResponse detail = jobs.detailFor(jobId, technicianId);
        return new MyJobResponse(detail, JobService.nextSteps(detail.job().status()));
    }
}
