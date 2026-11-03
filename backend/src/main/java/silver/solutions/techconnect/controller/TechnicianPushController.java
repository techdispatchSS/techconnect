package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.technician.RegisterPushDeviceRequest;
import silver.solutions.techconnect.dto.request.technician.UnregisterPushDeviceRequest;
import silver.solutions.techconnect.dto.response.technician.PushConfigResponse;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.PushService;

/**
 * A technician turning job alerts on or off for a device (#69). TECHNICIAN-only — enforced
 * centrally by the {@code /v1/technician/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/technician/push")
public class TechnicianPushController {

    private final PushService push;

    public TechnicianPushController(PushService push) {
        this.push = push;
    }

    @GetMapping("/config")
    public PushConfigResponse config() {
        return push.config();
    }

    @PutMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RegisterPushDeviceRequest request) {
        push.register(principal.id(), request.token(), request.platform());
    }

    @PostMapping("/devices/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UnregisterPushDeviceRequest request) {
        push.unregister(principal.id(), request.token());
    }
}
