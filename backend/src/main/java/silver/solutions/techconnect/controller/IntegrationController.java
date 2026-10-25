package silver.solutions.techconnect.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.dto.response.controller.FreshdeskStatusResponse;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskSyncService;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskUpdateService;
import silver.solutions.techconnect.integration.freshdesk.LiveFreshdeskClient;

/**
 * State of the Freshdesk connection for the dashboard header. CONTROLLER-only — enforced
 * centrally by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/controller/integrations")
public class IntegrationController {

    private final FreshdeskSyncService freshdesk;
    private final TechConnectProperties properties;
    private final FreshdeskUpdateService updates;
    private final boolean live;

    public IntegrationController(
            FreshdeskSyncService freshdesk,
            TechConnectProperties properties,
            FreshdeskUpdateService updates,
            ObjectProvider<LiveFreshdeskClient> liveClient) {
        this.freshdesk = freshdesk;
        this.properties = properties;
        this.updates = updates;
        this.live = liveClient.getIfAvailable() != null;
    }

    @GetMapping("/freshdesk")
    public FreshdeskStatusResponse freshdesk() {
        FreshdeskSyncService.Status status = freshdesk.status();
        String mode = !status.connected() ? "NOT_CONNECTED" : live ? "LIVE" : "SIMULATED";
        return new FreshdeskStatusResponse(
                mode,
                properties.simulationEnabled(),
                status.lastSyncAt(),
                status.lastSuccessAt(),
                status.lastError(),
                updates.effectiveMode().name());
    }
}
