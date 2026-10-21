package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.controller.CloseJobRequest;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.dto.response.controller.JobCountsResponse;
import silver.solutions.techconnect.dto.response.controller.JobDetailResponse;
import silver.solutions.techconnect.dto.response.controller.JobSummaryResponse;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.JobService;

/**
 * The controller's view of jobs in hand (FR-05, FR-07): Active jobs, and Review &amp; close.
 * CONTROLLER-only — enforced centrally by the {@code /v1/controller/**} rule in
 * {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/controller/jobs")
public class JobController {

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    public PageResponse<JobSummaryResponse> list(
            @RequestParam(defaultValue = "ACTIVE") JobService.Stage stage,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return jobs.list(stage, q, page, size);
    }

    @GetMapping("/counts")
    public JobCountsResponse counts() {
        return jobs.counts();
    }

    @GetMapping("/{id}")
    public JobDetailResponse get(@PathVariable UUID id) {
        return jobs.detail(id);
    }

    /** Signs off a submitted job; the incident closes with it. 409 unless it is submitted. */
    @PostMapping("/{id}/close")
    public JobDetailResponse close(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CloseJobRequest request) {
        jobs.close(id, principal.id(), request == null ? null : request.closingNotes());
        return jobs.detail(id);
    }
}
