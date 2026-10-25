package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;

/** A technician's own availability and location (FR-04/FR-05, T-14), and what it means for dispatch. */
class TechnicianAvailabilityIntegrationTest extends AbstractDispatchIntegrationTest {

    @Test
    void aTechnicianGoesOnAndOffDutyThemselves() throws Exception {
        User technician = technician("toggle", TechnicianStatus.OFFLINE);
        String token = tokenFor(technician);

        mvc.perform(get("/v1/technician/availability").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.onJob").value(false));

        // Asking twice is harmless: the second one changes nothing.
        for (int i = 0; i < 2; i++) {
            setAvailable(token, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AVAILABLE"));
        }
        assertThat(statusOf(technician)).isEqualTo(TechnicianStatus.AVAILABLE);

        setAvailable(token, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));
        assertThat(statusOf(technician)).isEqualTo(TechnicianStatus.OFFLINE);
    }

    @Test
    void aTechnicianOnAJobCantChangeTheirStatusOrBeSentAnother() throws Exception {
        String controller = controllerToken();
        User technician = technician("busy", TechnicianStatus.AVAILABLE);
        String token = tokenFor(technician);
        UUID dispatchId = dispatch(controller,
                incident("BusyCo", IncidentStatus.NEW, IncidentPriority.HIGH), "ASSIGN", technician);
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/technician/availability").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ON_JOB"))
                .andExpect(jsonPath("$.onJob").value(true));
        setAvailable(token, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Finish your current job before going off duty."));
        assertThat(statusOf(technician)).isEqualTo(TechnicianStatus.ON_JOB);

        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"ASSIGN","technicianIds":["%s"]}
                                """.formatted(
                                incident("SecondCo", IncidentStatus.NEW, IncidentPriority.LOW).getId(),
                                technician.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value(technician.getName() + " is already on a job and can't be dispatched."));
    }

    @Test
    void aReportedLocationIsKeptForTheDispatchList() throws Exception {
        User technician = technician("located", TechnicianStatus.AVAILABLE);
        String token = tokenFor(technician);

        reportLocation(token, """
                {"latitude":-33.9248685,"longitude":18.4240553}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLatitude").value(-33.924869))
                .andExpect(jsonPath("$.lastLongitude").value(18.424055))
                .andExpect(jsonPath("$.lastLocationAt").isNotEmpty());

        assertThat(technicianProfiles.findById(technician.getId())).get().satisfies(p -> {
            assertThat(p.getLastLatitude()).isEqualByComparingTo(new BigDecimal("-33.924869"));
            assertThat(p.getLastLongitude()).isEqualByComparingTo(new BigDecimal("18.424055"));
            assertThat(p.getLastLocationAt()).isNotNull();
        });
    }

    @Test
    void aLocationOffTheMapIsRefused() throws Exception {
        String token = tokenFor(technician("lost", TechnicianStatus.AVAILABLE));

        reportLocation(token, """
                {"latitude":95,"longitude":18.42}""").andExpect(status().isBadRequest());
        reportLocation(token, """
                {"latitude":-33.92}""").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTechniciansHaveAnAvailabilityOfTheirOwn() throws Exception {
        String controller = controllerToken();

        mvc.perform(get("/v1/technician/availability").header("Authorization", "Bearer " + controller))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/technician/availability")).andExpect(status().isUnauthorized());
    }

    private ResultActions setAvailable(String token, boolean available)
            throws Exception {
        return mvc.perform(put("/v1/technician/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":%s}".formatted(available)));
    }

    private ResultActions reportLocation(String token, String body)
            throws Exception {
        return mvc.perform(put("/v1/technician/location")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private TechnicianStatus statusOf(User technician) {
        return technicianProfiles.findById(technician.getId()).orElseThrow().getStatus();
    }
}
