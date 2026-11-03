package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.integration.push.PushSender;
import silver.solutions.techconnect.repository.PushDeviceRepository;

/** Job offers reaching a technician's device with the app closed (#69). */
class JobAlertsIntegrationTest extends AbstractDispatchIntegrationTest {

    @MockitoBean private PushSender sender;
    @Autowired private PushDeviceRepository devices;

    @BeforeEach
    void pushesSucceed() {
        clearInvocations(sender);
        when(sender.send(anyString(), any())).thenReturn(PushSender.Result.SENT);
    }

    private static String token() {
        return "token-" + UUID.randomUUID();
    }

    @Test
    void anOfferAlertsEveryDeviceTheInvitedTechnicianTurnedAlertsOnFor() throws Exception {
        User technician = technician("alerted", TechnicianStatus.AVAILABLE);
        String phone = token();
        String tablet = token();
        register(tokenFor(technician), phone).andExpect(status().isNoContent());
        register(tokenFor(technician), tablet).andExpect(status().isNoContent());

        dispatch(controllerToken(), incident("AlertCo", IncidentStatus.NEW, IncidentPriority.HIGH), "ASSIGN", technician);

        ArgumentCaptor<PushSender.PushMessage> message = ArgumentCaptor.forClass(PushSender.PushMessage.class);
        verify(sender).send(eq(phone), message.capture());
        verify(sender).send(eq(tablet), any());
        assertThat(message.getValue().title()).isEqualTo("New job offer");
        assertThat(message.getValue().body()).isEqualTo("AlertCo · AlertCo Office");
        assertThat(message.getValue().url()).isEqualTo("/jobs");
    }

    @Test
    void resendingAlertsThemAgainAsAReminder() throws Exception {
        String controller = controllerToken();
        User technician = technician("reminded", TechnicianStatus.AVAILABLE);
        String phone = token();
        register(tokenFor(technician), phone);
        UUID dispatchId = dispatch(controller, incident("NudgeCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", technician);
        clearInvocations(sender);
        // The resend cooldown looks at the last notification, so move that back.
        var sent = dispatches.findById(dispatchId).orElseThrow();
        sent.setLastNotifiedAt(Instant.now().minusSeconds(300));
        dispatches.save(sent);

        mvc.perform(post("/v1/controller/dispatches/{id}/resend", dispatchId)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk());

        ArgumentCaptor<PushSender.PushMessage> message = ArgumentCaptor.forClass(PushSender.PushMessage.class);
        verify(sender).send(eq(phone), message.capture());
        assertThat(message.getValue().title()).startsWith("Reminder");
    }

    @Test
    void aTechnicianNotInvitedHearsNothing() throws Exception {
        User invited = technician("invited", TechnicianStatus.AVAILABLE);
        User other = technician("other", TechnicianStatus.AVAILABLE);
        String otherPhone = token();
        register(tokenFor(other), otherPhone);

        dispatch(controllerToken(), incident("QuietCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", invited);

        verify(sender, never()).send(eq(otherPhone), any());
    }

    @Test
    void aDeadTokenIsForgottenAndTheDispatchStillGoesOut() throws Exception {
        User technician = technician("deadphone", TechnicianStatus.AVAILABLE);
        String gone = token();
        register(tokenFor(technician), gone);
        when(sender.send(eq(gone), any())).thenReturn(PushSender.Result.DEAD_TOKEN);

        UUID dispatchId = dispatch(controllerToken(), incident("DeadCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", technician);

        assertThat(dispatches.findById(dispatchId)).isPresent();
        assertThat(devices.findByToken(gone)).isEmpty();
    }

    @Test
    void aFailingPushDoesNotFailTheDispatch() throws Exception {
        User technician = technician("failing", TechnicianStatus.AVAILABLE);
        String phone = token();
        register(tokenFor(technician), phone);
        when(sender.send(eq(phone), any())).thenThrow(new IllegalStateException("FCM is down"));

        UUID dispatchId = dispatch(controllerToken(), incident("DownCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", technician);

        assertThat(dispatches.findById(dispatchId)).isPresent();
        assertThat(devices.findByToken(phone)).isPresent();
    }

    @Test
    void aSharedPhoneAlertsOnlyWhoeverRegisteredItLast() throws Exception {
        User first = technician("firstuser", TechnicianStatus.AVAILABLE);
        User second = technician("seconduser", TechnicianStatus.AVAILABLE);
        String phone = token();
        register(tokenFor(first), phone);
        register(tokenFor(second), phone);

        dispatch(controllerToken(), incident("SharedCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", first);

        verify(sender, never()).send(eq(phone), any());
        assertThat(devices.findByToken(phone)).get()
                .satisfies(d -> assertThat(d.getUserId()).isEqualTo(second.getId()));
    }

    @Test
    void turningAlertsOffStopsThemAndOnlyWorksOnYourOwnDevice() throws Exception {
        User owner = technician("owner", TechnicianStatus.AVAILABLE);
        String phone = token();
        register(tokenFor(owner), phone);

        unregister(tokenFor(technician("stranger", TechnicianStatus.AVAILABLE)), phone)
                .andExpect(status().isNoContent());
        assertThat(devices.findByToken(phone)).isPresent();

        unregister(tokenFor(owner), phone).andExpect(status().isNoContent());
        assertThat(devices.findByToken(phone)).isEmpty();

        dispatch(controllerToken(), incident("OffCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", owner);
        verify(sender, never()).send(eq(phone), any());
    }

    @Test
    void aDeviceNeedsATokenAndAKnownPlatform() throws Exception {
        String token = tokenFor(technician("invalid", TechnicianStatus.AVAILABLE));

        mvc.perform(put("/v1/technician/push/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\" \",\"platform\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/technician/push/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAppIsToldAlertsAreOffUntilFirebaseIsSetUp() throws Exception {
        mvc.perform(get("/v1/technician/push/config")
                        .header("Authorization", "Bearer " + tokenFor(technician("config", TechnicianStatus.AVAILABLE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void onlyTechniciansRegisterDevices() throws Exception {
        register(controllerToken(), token()).andExpect(status().isForbidden());
    }

    private ResultActions register(String bearer, String token) throws Exception {
        return mvc.perform(put("/v1/technician/push/devices")
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"platform\":\"WEB\"}".formatted(token)));
    }

    private ResultActions unregister(String bearer, String token) throws Exception {
        return mvc.perform(post("/v1/technician/push/devices/unregister")
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }
}
