package silver.solutions.techconnect.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.response.controller.JobDetailResponse;
import silver.solutions.techconnect.service.JobService;

/**
 * A job's audit trail for the Manager (FR-09). Read-only, as the trail is for everyone.
 * MANAGER-only — enforced centrally by the {@code /v1/admin/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/admin/jobs")
public class AdminJobController {

    private final JobService jobs;

    public AdminJobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/{id}/history")
    public List<JobDetailResponse.HistoryEntry> history(@PathVariable UUID id) {
        return jobs.history(id);
    }
}
