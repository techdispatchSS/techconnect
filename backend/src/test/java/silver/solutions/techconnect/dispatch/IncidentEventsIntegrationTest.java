package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.service.DispatchLifecycleService;

/** A ticket's history (FR-09): who logged, dispatched, nudged, answered and closed it. */
class IncidentEventsIntegrationTest extends AbstractDispatchIntegrationTest {

    @Autowired private DispatchLifecycleService lifecycle;
    @Autowired private JobRepository jobs;
    @Autowired private JdbcTemplate jdbc;

    private List<String> types(String controller, UUID incidentId) throws Exception {
        return JsonPath.read(timeline(controller, incidentId), "$[*].type");
    }

    private static Map<String, Object> lastEvent(String body) {
        List<Map<String, Object>> events = JsonPath.read(body, "$");
        return events.get(events.size() - 1);
    }

    private String timeline(String controller, UUID incidentId) throws Exception {
        return mvc.perform(get("/v1/controller/incidents/{id}/events", incidentId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aTicketRecordsEachStepFromLoggingToClosing() throws Exception {
        String controller = controllerToken();

        String logged = mvc.perform(post("/v1/controller/incidents")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientName":"History Co","priority":"HIGH"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID incidentId = UUID.fromString(JsonPath.read(logged, "$.id"));
        Incident incident = incidents.findById(incidentId).orElseThrow();

        User thabo = technician("hist-thabo", TechnicianStatus.AVAILABLE);
        User sipho = technician("hist-sipho", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", thabo, sipho);

        // Past the 60-second cooldown, so the nudge is allowed.
        Dispatch sent = dispatches.findById(dispatchId).orElseThrow();
        sent.setLastNotifiedAt(Instant.now().minusSeconds(120));
        dispatches.save(sent);
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(sipho)))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(thabo)))
                .andExpect(status().isOk());

        UUID jobId = jobs.findByDispatchId(dispatchId).orElseThrow().getId();
        for (String step : List.of("ON_SITE", "WORK_STARTED", "COMPLETE")) {
            mvc.perform(post("/v1/controller/simulation/jobs/{id}/status", jobId)
                            .header("Authorization", "Bearer " + controller)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"%s\"}".formatted(step)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/v1/controller/simulation/jobs/{id}/report", jobId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/controller/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        String body = timeline(controller, incidentId);
        assertThat(types(controller, incidentId)).containsExactly(
                "INCIDENT_LOGGED", "DISPATCH_SENT", "DISPATCH_RESENT",
                "DISPATCH_DECLINED", "DISPATCH_ACCEPTED", "INCIDENT_CLOSED");

        // The controller did the logging, sending, nudging and closing; each technician their own answer.
        assertThat((List<String>) JsonPath.read(body, "$[*].actorName")).containsExactly(
                "Test Controller", "Test Controller", "Test Controller",
                sipho.getName(), thabo.getName(), "Test Controller");
        assertThat((String) JsonPath.read(body, "$[1].summary"))
                .isEqualTo("Broadcast to " + sipho.getName() + ", " + thabo.getName());
        assertThat((String) JsonPath.read(body, "$[2].summary"))
                .isEqualTo("Notified again: " + sipho.getName() + ", " + thabo.getName());
    }

    @Test
    void aDispatchThatLapsesIsRecordedAsTheSystemDoingIt() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("LapseHistoryCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        User technician = technician("hist-lapse", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        Dispatch dispatch = dispatches.findById(dispatchId).orElseThrow();
        dispatch.setExpiresAt(Instant.now().minusSeconds(5));
        dispatches.save(dispatch);
        lifecycle.expireDue();

        String body = timeline(controller, incident.getId());
        Map<String, Object> last = lastEvent(body);
        assertThat(last.get("type")).isEqualTo("DISPATCH_EXPIRED");
        assertThat(last.get("summary")).isEqualTo("Nobody accepted in time");
        assertThat(last.get("actorName")).as("the system, not a person").isNull();
    }

    @Test
    void whenEveryoneDeclinesTheHistorySaysSo() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("DeclineHistoryCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("hist-decline", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isOk());

        String body = timeline(controller, incident.getId());
        assertThat((List<String>) JsonPath.read(body, "$[*].type"))
                .containsSubsequence("DISPATCH_SENT", "DISPATCH_DECLINED", "DISPATCH_EXPIRED");
        assertThat(lastEvent(body).get("summary")).isEqualTo("Everyone declined");
    }

    @Test
    void anActionThatIsRefusedLeavesNoTrace() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("RefusedCo", IncidentStatus.CLOSED, IncidentPriority.LOW);
        User technician = technician("hist-refused", TechnicianStatus.AVAILABLE);
        int before = types(controller, incident.getId()).size();

        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"ASSIGN","technicianIds":["%s"]}
                                """.formatted(incident.getId(), technician.getId())))
                .andExpect(status().isConflict());

        assertThat(types(controller, incident.getId())).hasSize(before);
    }

    @Test
    void theHistoryCannotBeEditedOrDeletedEvenDirectlyInTheDatabase() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("ImmutableCo", IncidentStatus.NEW, IncidentPriority.LOW);
        dispatch(controller, incident, "ASSIGN", technician("hist-immutable", TechnicianStatus.AVAILABLE));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE incident_events SET summary = 'tampered' WHERE incident_id = ?", incident.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM incident_events WHERE incident_id = ?", incident.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    void onlyAControllerCanReadAHistoryAndAnUnknownTicketIsNotFound() throws Exception {
        Incident incident = incident("PrivateCo", IncidentStatus.NEW, IncidentPriority.LOW);
        String technicianToken = tokenFor(technician("hist-nosy", TechnicianStatus.OFFLINE));

        mvc.perform(get("/v1/controller/incidents/{id}/events", incident.getId())
                        .header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/controller/incidents/{id}/events", UUID.randomUUID())
                        .header("Authorization", "Bearer " + controllerToken()))
                .andExpect(status().isNotFound());
    }
}
