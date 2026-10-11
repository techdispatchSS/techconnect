package silver.solutions.techconnect.dispatch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * What a real deployment looks like: no simulation. The routes must not exist (a 404, not
 * merely a refusal) and the dashboard must say plainly that Freshdesk is not connected.
 */
@TestPropertySource(properties = "techconnect.simulation.enabled=false")
class SimulationDisabledIntegrationTest extends AbstractDispatchIntegrationTest {

    @Test
    void thereIsNoWayToMakeUpTicketsOrTechnicianAnswers() throws Exception {
        String controller = controllerToken();

        mvc.perform(post("/v1/controller/simulation/freshdesk-tickets")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isNotFound());

        mvc.perform(post("/v1/controller/simulation/dispatches/{id}/respond", UUID.randomUUID())
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technicianId":"%s","response":"ACCEPTED"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void theHeaderStatusReportsFreshdeskAsNotConnected() throws Exception {
        mvc.perform(get("/v1/controller/integrations/freshdesk")
                        .header("Authorization", "Bearer " + controllerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.simulation").value(false));
    }
}
