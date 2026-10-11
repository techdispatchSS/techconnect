package silver.solutions.techconnect.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.dto.response.controller.FreshdeskStatusResponse;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskSyncService;

/**
 * State of the Freshdesk connection for the dashboard header. CONTROLLER-only — enforced
 * centrally by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/controller/integrations")
public class IntegrationController {

    private final FreshdeskSyncService freshdesk;
    private final TechConnectProperties properties;

    public IntegrationController(FreshdeskSyncService freshdesk, TechConnectProperties properties) {
        this.freshdesk = freshdesk;
        this.properties = properties;
    }

    @GetMapping("/freshdesk")
    public FreshdeskStatusResponse freshdesk() {
        FreshdeskSyncService.Status status = freshdesk.status();
        // Only a simulated Freshdesk exists today; a live one will report its own mode here.
        String mode = status.connected() ? "SIMULATED" : "NOT_CONNECTED";
        return new FreshdeskStatusResponse(
                mode,
                properties.simulationEnabled(),
                status.lastSyncAt(),
                status.lastSuccessAt(),
                status.lastError());
    }
}
