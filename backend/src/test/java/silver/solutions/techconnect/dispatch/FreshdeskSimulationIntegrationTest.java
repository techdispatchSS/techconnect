package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskSyncService;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskTicket;
import silver.solutions.techconnect.integration.freshdesk.SimulatedFreshdeskClient;

/**
 * The simulated Freshdesk, end to end: a ticket raised there reaches the dashboard through the
 * same sync a live Freshdesk would use, and a repeat sync never duplicates it.
 */
class FreshdeskSimulationIntegrationTest extends AbstractDispatchIntegrationTest {

    @Autowired private SimulatedFreshdeskClient freshdesk;
    @Autowired private FreshdeskSyncService sync;

    @Test
    void aRaisedTicketAppearsInTheQueueAsANewIncident() throws Exception {
        String controller = controllerToken();

        String body = mvc.perform(post("/v1/controller/simulation/freshdesk-tickets")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(org.hamcrest.Matchers.startsWith("SIM-")))
                .andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(body, "$.ticketId");

        Incident incident = incidents.findByFreshdeskTicketId(ticketId).orElseThrow();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
        assertThat(incident.getClientName()).isNotBlank();
        assertThat(incident.getSiteAddress()).isNotBlank();
        assertThat(incident.getSiteLatitude()).isNotNull();
        assertThat(incident.getSiteLongitude()).isNotNull();
        assertThat(incident.getPriority()).isNotNull();
        assertThat(incident.getSlaDueAt()).isAfter(Instant.now());

        mvc.perform(get("/v1/controller/incidents/{id}", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.ticketRef").value(ticketId));
    }

    @Test
    void anOverdueTicketIsAlreadyPastItsSlaWhenItArrives() throws Exception {
        String controller = controllerToken();

        String body = mvc.perform(post("/v1/controller/simulation/freshdesk-tickets")
                        .param("overdue", "true")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Incident incident = incidents.findByFreshdeskTicketId(JsonPath.read(body, "$.ticketId"))
                .orElseThrow();
        assertThat(incident.isOverdue()).isTrue();
        assertThat(incident.getCreatedAt()).isBefore(Instant.now().minusSeconds(60));
    }

    @Test
    void syncingTwiceDoesNotDuplicateOrRewriteAnIncident() {
        FreshdeskTicket ticket = freshdesk.raise(false);

        FreshdeskSyncService.Result first = sync.sync();
        FreshdeskSyncService.Result second = sync.sync();

        assertThat(first.created()).isGreaterThanOrEqualTo(1);
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(incidents.findAll().stream()
                        .filter(i -> ticket.id().equals(i.getFreshdeskTicketId())))
                .hasSize(1);
    }

    @Test
    void anEditInFreshdeskUpdatesDetailsButNeverUndoesOurOwnWorkflow() throws Exception {
        String controller = controllerToken();
        FreshdeskTicket ticket = freshdesk.raise(false);
        sync.sync();
        Incident incident = incidents.findByFreshdeskTicketId(ticket.id()).orElseThrow();

        // A Controller dispatches it, so it is IN_PROGRESS on our side.
        User technician = technician("fd-tech", TechnicianStatus.AVAILABLE);
        dispatch(controller, incident, "ASSIGN", technician);
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.IN_PROGRESS);

        // An agent bumps the priority in Freshdesk (ticket still open there).
        freshdesk.amend(ticket.id(), t -> new FreshdeskTicket(
                t.id(), t.subject(), t.descriptionText(), t.type(), 1, FreshdeskTicket.STATUS_OPEN,
                t.companyName(), t.requesterName(), t.requesterPhone(), t.dueBy(), t.createdAt(),
                t.updatedAt(), t.customFields()));
        assertThat(sync.sync().updated()).isGreaterThanOrEqualTo(1);

        Incident after = incidents.findById(incident.getId()).orElseThrow();
        assertThat(after.getPriority()).isEqualTo(IncidentPriority.LOW);
        assertThat(after.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
    }

    @Test
    void resolvingTheTicketInFreshdeskClosesTheIncident() {
        FreshdeskTicket ticket = freshdesk.raise(false);
        sync.sync();

        freshdesk.amend(ticket.id(), t -> new FreshdeskTicket(
                t.id(), t.subject(), t.descriptionText(), t.type(), t.priority(),
                FreshdeskTicket.STATUS_RESOLVED, t.companyName(), t.requesterName(),
                t.requesterPhone(), t.dueBy(), t.createdAt(), t.updatedAt(), t.customFields()));
        FreshdeskSyncService.Result result = sync.sync();

        assertThat(result.closed()).isGreaterThanOrEqualTo(1);
        assertThat(incidents.findByFreshdeskTicketId(ticket.id()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.CLOSED);
    }

    @Test
    void aTicketAlreadyResolvedWhenFirstSeenIsNotImported() {
        FreshdeskTicket ticket = freshdesk.raise(false);
        freshdesk.amend(ticket.id(), t -> new FreshdeskTicket(
                t.id(), t.subject(), t.descriptionText(), t.type(), t.priority(),
                FreshdeskTicket.STATUS_CLOSED, t.companyName(), t.requesterName(),
                t.requesterPhone(), t.dueBy(), t.createdAt(), t.updatedAt(), t.customFields()));

        FreshdeskSyncService.Result result = sync.sync();

        assertThat(result.skipped()).isGreaterThanOrEqualTo(1);
        assertThat(incidents.findByFreshdeskTicketId(ticket.id())).isEmpty();
    }

    @Test
    void reportsTheConnectionHonestly() throws Exception {
        String controller = controllerToken();
        sync.sync();

        mvc.perform(get("/v1/controller/integrations/freshdesk")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SIMULATED"))
                .andExpect(jsonPath("$.simulation").value(true))
                .andExpect(jsonPath("$.lastSyncAt").isNotEmpty());
    }

    @Test
    void aSimulatedTechnicianAnswerGoesThroughTheRealLifecycle() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("SimAnswerCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("sim-answer", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        mvc.perform(post("/v1/controller/simulation/dispatches/{id}/respond", dispatchId)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technicianId":"%s","response":"ACCEPTED"}""".formatted(technician.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));

        mvc.perform(get("/v1/controller/incidents/{id}/progress", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.dispatchStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedTechnicianName").value(technician.getName()));

        // PENDING/NO_RESPONSE are not answers a person can give.
        mvc.perform(post("/v1/controller/simulation/dispatches/{id}/respond", dispatchId)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technicianId":"%s","response":"NO_RESPONSE"}""".formatted(technician.getId())))
                .andExpect(status().isBadRequest());
    }
}
