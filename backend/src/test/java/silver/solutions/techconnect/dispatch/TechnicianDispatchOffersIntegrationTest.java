package silver.solutions.techconnect.dispatch;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;

/** The jobs offered to a technician that are still waiting on their answer (FR-04). */
class TechnicianDispatchOffersIntegrationTest extends AbstractDispatchIntegrationTest {

    @Test
    void anOfferShowsWhatTheTechnicianNeedsToDecideButNotTheContactYet() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("OfferCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        incident.setIssueType("CCTV");
        incident.setDescription("Cameras on the east fence are down.");
        incident.setContactName("Sipho");
        incident.setContactPhone("+27 82 000 0000");
        // Where the fixture technician last was: zero km away.
        incident.setSiteLatitude(new BigDecimal("-26.107600"));
        incident.setSiteLongitude(new BigDecimal("28.056700"));
        incidents.save(incident);
        User technician = technician("offered", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        offers(tokenFor(technician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dispatchId").value(dispatchId.toString()))
                .andExpect(jsonPath("$[0].dispatchType").value("ASSIGN"))
                .andExpect(jsonPath("$[0].clientName").value("OfferCo"))
                .andExpect(jsonPath("$[0].siteAddress").value("OfferCo Office"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].issueType").value("CCTV"))
                .andExpect(jsonPath("$[0].description").value("Cameras on the east fence are down."))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$[0].contactName").doesNotExist())
                .andExpect(jsonPath("$[0].contactPhone").doesNotExist());
    }

    @Test
    void aTechnicianOnlySeesOffersMadeToThem() throws Exception {
        String controller = controllerToken();
        User invited = technician("invited", TechnicianStatus.AVAILABLE);
        User other = technician("other", TechnicianStatus.AVAILABLE);
        dispatch(controller, incident("MineCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", invited);

        offers(tokenFor(invited)).andExpect(jsonPath("$", hasSize(1)));
        offers(tokenFor(other)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anOfferGoesOnceItIsAnsweredTakenOrLapsed() throws Exception {
        String controller = controllerToken();
        User first = technician("taker", TechnicianStatus.AVAILABLE);
        User second = technician("slower", TechnicianStatus.AVAILABLE);
        User decliner = technician("decliner", TechnicianStatus.AVAILABLE);

        UUID broadcast = dispatch(controller,
                incident("RaceCo", IncidentStatus.NEW, IncidentPriority.HIGH), "BROADCAST", first, second);
        UUID declined = dispatch(controller,
                incident("NoCo", IncidentStatus.NEW, IncidentPriority.LOW), "BROADCAST", decliner, second);
        UUID lapsed = dispatch(controller,
                incident("LateCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", decliner);
        offers(tokenFor(second)).andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", broadcast)
                        .header("Authorization", "Bearer " + tokenFor(first)))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/technician/dispatches/{id}/decline", declined)
                        .header("Authorization", "Bearer " + tokenFor(decliner)))
                .andExpect(status().isOk());
        Dispatch late = dispatches.findById(lapsed).orElseThrow();
        late.setExpiresAt(Instant.now().minusSeconds(1));
        dispatches.save(late);

        // Taken by someone else: gone. Declined by another invitee: still open for this one.
        offers(tokenFor(second))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dispatchId").value(declined.toString()));
        // Answered it themselves, and the other one's window has passed.
        offers(tokenFor(decliner)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyTechniciansHaveOffers() throws Exception {
        offers(controllerToken()).andExpect(status().isForbidden());
    }

    private ResultActions offers(String token) throws Exception {
        return mvc.perform(get("/v1/technician/dispatches").header("Authorization", "Bearer " + token));
    }
}
