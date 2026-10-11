package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.service.NotificationService.DispatchNotice;

/**
 * What happens after a dispatch is sent: technicians are told, the ticket says how it stands
 * ("sent to 3, awaiting confirmation"), and the Controller can nudge those who have not answered.
 */
class DispatchNotificationIntegrationTest extends AbstractDispatchIntegrationTest {

    @Test
    void sendingADispatchNotifiesEveryInvitedTechnicianOnce() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("NoticeCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User one = technician("notice-one", TechnicianStatus.AVAILABLE);
        User two = technician("notice-two", TechnicianStatus.AVAILABLE);
        reset(notifications);

        UUID dispatchId = dispatch(controller, incident, "BROADCAST", one, two);

        ArgumentCaptor<DispatchNotice> notice = ArgumentCaptor.forClass(DispatchNotice.class);
        verify(notifications).sendDispatchNotice(
                org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(one.getId())), notice.capture());
        verify(notifications).sendDispatchNotice(
                org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(two.getId())), any());
        assertThat(notice.getValue().reminder()).isFalse();
        assertThat(notice.getValue().clientName()).isEqualTo("NoticeCo");
        assertThat(notice.getValue().ticketRef()).startsWith("INC-");

        Dispatch saved = dispatches.findById(dispatchId).orElseThrow();
        assertThat(saved.getNotificationCount()).isEqualTo(1);
        assertThat(saved.getLastNotifiedAt()).isNotNull();
    }

    @Test
    void theTicketSaysHowManyTechniciansItWentToAndWhoIsStillToAnswer() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("StateCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User one = technician("state-one", TechnicianStatus.AVAILABLE);
        User two = technician("state-two", TechnicianStatus.AVAILABLE);
        User three = technician("state-three", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", one, two, three);

        mvc.perform(get("/v1/controller/incidents/{id}", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDispatch").value(true))
                .andExpect(jsonPath("$.dispatch.id").value(dispatchId.toString()))
                .andExpect(jsonPath("$.dispatch.status").value("PENDING"))
                .andExpect(jsonPath("$.dispatch.invited").value(3))
                .andExpect(jsonPath("$.dispatch.pending").value(3))
                .andExpect(jsonPath("$.dispatch.declined").value(0))
                .andExpect(jsonPath("$.dispatch.notificationCount").value(1));

        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(one)))
                .andExpect(status().isOk());

        // The queue rows carry the same summary, so it shows without opening the ticket.
        mvc.perform(get("/v1/controller/incidents").param("q", "StateCo")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.content[0].dispatch.invited").value(3))
                .andExpect(jsonPath("$.content[0].dispatch.pending").value(2))
                .andExpect(jsonPath("$.content[0].dispatch.declined").value(1));
    }

    @Test
    void anIncidentWithNoDispatchHasNoSummary() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("NoneCo", IncidentStatus.NEW, IncidentPriority.LOW);

        mvc.perform(get("/v1/controller/incidents/{id}", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.hasDispatch").value(false))
                .andExpect(jsonPath("$.dispatch").doesNotExist());
    }

    @Test
    void anAcceptedDispatchNamesWhoTookIt() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("TakenCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User one = technician("taken-one", TechnicianStatus.AVAILABLE);
        User two = technician("taken-two", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", one, two);

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(two)))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/controller/incidents/{id}", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.dispatch.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.dispatch.acceptedTechnicianName").value(two.getName()))
                .andExpect(jsonPath("$.dispatch.noResponse").value(1));
    }

    @Test
    void resendingNotifiesOnlyThoseWhoHaveNotAnswered() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("ResendCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User declined = technician("resend-declined", TechnicianStatus.AVAILABLE);
        User waiting = technician("resend-waiting", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "BROADCAST", declined, waiting);
        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(declined)))
                .andExpect(status().isOk());
        notifiedLongAgo(dispatchId);
        reset(notifications);

        mvc.perform(post("/v1/controller/dispatches/{id}/resend", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notified").value(1))
                .andExpect(jsonPath("$.notificationCount").value(2));

        ArgumentCaptor<DispatchNotice> notice = ArgumentCaptor.forClass(DispatchNotice.class);
        verify(notifications, times(1)).sendDispatchNotice(
                org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(waiting.getId())),
                notice.capture());
        verify(notifications, never()).sendDispatchNotice(
                org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(declined.getId())), any());
        assertThat(notice.getValue().reminder()).isTrue();

        // The window is untouched: a nudge is not a fresh ten minutes.
        Dispatch after = dispatches.findById(dispatchId).orElseThrow();
        assertThat(after.getNotificationCount()).isEqualTo(2);
        assertThat(after.getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void resendingTwiceInARowIsRefusedUntilTheCooldownPasses() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("CooldownCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("cooldown", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        // Just sent with the dispatch itself, so an immediate resend is a double-click, not a nudge.
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("resend in")));

        notifiedLongAgo(dispatchId);
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/controller/dispatches/{id}/resend", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotResendOnceItHasBeenAcceptedOrHasExpired() throws Exception {
        String controller = controllerToken();

        Incident taken = incident("AcceptedCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User acceptor = technician("resend-accept", TechnicianStatus.AVAILABLE);
        UUID acceptedId = dispatch(controller, taken, "ASSIGN", acceptor);
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", acceptedId)
                        .header("Authorization", "Bearer " + tokenFor(acceptor)))
                .andExpect(status().isOk());
        notifiedLongAgo(acceptedId);
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", acceptedId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A technician has already accepted this job."));

        Incident lapsed = incident("LapsedCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User silent = technician("resend-silent", TechnicianStatus.AVAILABLE);
        UUID lapsedId = dispatch(controller, lapsed, "ASSIGN", silent);
        Dispatch dispatch = dispatches.findById(lapsedId).orElseThrow();
        dispatch.setExpiresAt(Instant.now().minusSeconds(5));
        dispatch.setLastNotifiedAt(Instant.now().minusSeconds(3600));
        dispatches.save(dispatch);
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", lapsedId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void onlyAControllerCanResendAndAnUnknownDispatchIsNotFound() throws Exception {
        String controller = controllerToken();
        User technician = technician("resend-role", TechnicianStatus.AVAILABLE);

        mvc.perform(post("/v1/controller/dispatches/{id}/resend", UUID.randomUUID())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isNotFound());
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/controller/dispatches/{id}/resend", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    /** Backdates the last notification so the resend cooldown has passed. */
    private void notifiedLongAgo(UUID dispatchId) {
        Dispatch dispatch = dispatches.findById(dispatchId).orElseThrow();
        dispatch.setLastNotifiedAt(Instant.now().minusSeconds(3600));
        dispatches.save(dispatch);
    }
}
