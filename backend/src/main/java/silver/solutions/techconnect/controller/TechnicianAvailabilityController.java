package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.technician.ReportLocationRequest;
import silver.solutions.techconnect.dto.request.technician.UpdateAvailabilityRequest;
import silver.solutions.techconnect.dto.response.technician.TechnicianAvailabilityResponse;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.TechnicianAvailabilityService;

/**
 * A technician's own availability and location (FR-05, T-14). TECHNICIAN-only — enforced
 * centrally by the {@code /v1/technician/**} rule in {@code SecurityConfig}. Always about the
 * authenticated caller, so nobody can put someone else on or off duty.
 */
@RestController
@RequestMapping("/v1/technician")
public class TechnicianAvailabilityController {

    private final TechnicianAvailabilityService availability;

    public TechnicianAvailabilityController(TechnicianAvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping("/availability")
    public TechnicianAvailabilityResponse get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return availability.current(principal.id());
    }

    /** On or off duty. 409 while they hold a job that is not complete yet. */
    @PutMapping("/availability")
    public TechnicianAvailabilityResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        return availability.setAvailable(principal.id(), request.available());
    }

    @PutMapping("/location")
    public TechnicianAvailabilityResponse reportLocation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReportLocationRequest request) {
        return availability.reportLocation(principal.id(), request.latitude(), request.longitude());
    }
}
