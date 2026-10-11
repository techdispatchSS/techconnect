package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.service.DispatchLifecycleService;

/** A dispatch after it has gone out (FR-04): answers, first-accept-wins, expiry and re-queueing. */
class DispatchLifecycleIntegrationTest extends AbstractDispatchIntegrationTest {

    @Autowired private DispatchLifecycleService lifecycle;

    @Test
    void theFirstAcceptWinsAndTheOthersAreToldTheJobIsGone() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("RaceCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User first = technician("first", TechnicianStatus.AVAILABLE);
        User second = technician("second", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", first, second);

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Another technician has already accepted this job."));

        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.ACCEPTED));
        assertThat(responseOf(dispatchId, first)).isEqualTo(DispatchResponseStatus.ACCEPTED);
        assertThat(responseOf(dispatchId, second)).isEqualTo(DispatchResponseStatus.NO_RESPONSE);
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS));

        mvc.perform(get("/v1/controller/incidents/{id}/progress", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedTechnicianName").value(first.getName()));
    }

    @Test
    void repeatingTheWinningAcceptIsHarmless() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("RetryCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        User technician = technician("retry", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);
        String token = tokenFor(technician);

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("ACCEPTED"));
        }
    }

    @Test
    void aDeclineKeepsTheDispatchOpenForTheOthersUntilEveryoneHasDeclined() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("DeclineCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User one = technician("decline-one", TechnicianStatus.AVAILABLE);
        User two = technician("decline-two", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", one, two);

        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(one)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DECLINED"));

        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.PENDING));
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS));

        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(two)))
                .andExpect(status().isOk());

        // Nobody left who could take it: it lapses at once and the incident is back in the queue.
        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.EXPIRED));
        assertThat(responseOf(dispatchId, one)).isEqualTo(DispatchResponseStatus.DECLINED);
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.NEW));
    }

    @Test
    void theExpirySweepLapsAnUnansweredDispatchAndRequeuesTheIncident() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("SweepCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User silent = technician("silent", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", silent);

        // Still inside its window: the sweep must leave it alone.
        lifecycle.expireDue();
        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.PENDING));

        Dispatch dispatch = dispatches.findById(dispatchId).orElseThrow();
        dispatch.setExpiresAt(Instant.now().minusSeconds(5));
        dispatches.save(dispatch);

        assertThat(lifecycle.expireDue()).isGreaterThanOrEqualTo(1);

        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.EXPIRED));
        assertThat(responseOf(dispatchId, silent)).isEqualTo(DispatchResponseStatus.NO_RESPONSE);
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.NEW));

        // Too late to accept now, and the answer says why.
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(silent)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("This dispatch has expired and can no longer be accepted."));
    }

    @Test
    void anAcceptArrivingAfterTheWindowButBeforeTheSweepIsStillRefused() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("LateCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("late", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        Dispatch dispatch = dispatches.findById(dispatchId).orElseThrow();
        dispatch.setExpiresAt(Instant.now().minusSeconds(1));
        dispatches.save(dispatch);

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isConflict());

        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.EXPIRED));
    }

    @Test
    void aTechnicianWhoWasNotInvitedCannotTellTheDispatchExists() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("PrivateCo", IncidentStatus.NEW, IncidentPriority.LOW);
        User invited = technician("invited", TechnicianStatus.AVAILABLE);
        User outsider = technician("outsider", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", invited);

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(outsider)))
                .andExpect(status().isNotFound());

        assertThat(dispatches.findById(dispatchId)).get()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DispatchStatus.PENDING));
    }

    @Test
    void roleBoundariesHold() throws Exception {
        String controller = controllerToken();
        User technician = technician("roles", TechnicianStatus.AVAILABLE);
        Incident incident = incident("RoleCo", IncidentStatus.NEW, IncidentPriority.LOW);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + tokenFor(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anIncidentCannotHaveTwoLiveDispatchesButCanBeDispatchedAgainOnceItLapses() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("TwiceCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("twice", TechnicianStatus.AVAILABLE);
        UUID first = dispatch(controller, incident, "ASSIGN", technician);

        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"ASSIGN","technicianIds":["%s"]}
                                """.formatted(incident.getId(), technician.getId())))
                .andExpect(status().isConflict());

        Dispatch dispatch = dispatches.findById(first).orElseThrow();
        dispatch.setExpiresAt(Instant.now().minusSeconds(1));
        dispatches.save(dispatch);

        // A window that has passed no longer blocks, even before the sweep has reached it.
        UUID second = dispatch(controller, incident, "ASSIGN", technician);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void refusesToDispatchToAnInactiveTechnicianOrAClosedIncidentOrTwoAssignees() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("GuardCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User pending = technician("pending", TechnicianStatus.OFFLINE, UserStatus.PENDING_ACTIVATION);
        User disabled = technician("disabled", TechnicianStatus.OFFLINE, UserStatus.DISABLED);
        User active = technician("active", TechnicianStatus.AVAILABLE);
        User otherActive = technician("active-two", TechnicianStatus.AVAILABLE);

        for (User notActive : List.of(pending, disabled)) {
            mvc.perform(post("/v1/controller/dispatches")
                            .header("Authorization", "Bearer " + controller)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"incidentId":"%s","dispatchType":"BROADCAST","technicianIds":["%s","%s"]}
                                    """.formatted(incident.getId(), active.getId(), notActive.getId())))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"ASSIGN","technicianIds":["%s","%s"]}
                                """.formatted(incident.getId(), active.getId(), otherActive.getId())))
                .andExpect(status().isBadRequest());

        Incident closed = incident("ClosedCo", IncidentStatus.CLOSED, IncidentPriority.LOW);
        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"ASSIGN","technicianIds":["%s"]}
                                """.formatted(closed.getId(), active.getId())))
                .andExpect(status().isConflict());

        assertThat(dispatches.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
    }

    private DispatchResponseStatus responseOf(UUID dispatchId, User technician) {
        return dispatchResponses.findByDispatchIdAndTechnicianId(dispatchId, technician.getId())
                .orElseThrow().getResponse();
    }
}
