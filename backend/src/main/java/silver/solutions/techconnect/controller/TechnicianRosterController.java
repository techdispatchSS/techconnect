package silver.solutions.techconnect.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.dto.response.controller.TechnicianRosterEntry;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.service.TechnicianRosterService;

/**
 * The controller's read-only technician roster. There is nothing here that changes a
 * technician: adding, editing and offboarding are the Manager's. CONTROLLER-only — enforced
 * centrally by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/controller/technicians")
public class TechnicianRosterController {

    private final TechnicianRosterService roster;

    public TechnicianRosterController(TechnicianRosterService roster) {
        this.roster = roster;
    }

    @GetMapping
    public PageResponse<TechnicianRosterEntry> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TechnicianStatus availability,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return roster.list(q, availability, includeDisabled, page, size);
    }
}
