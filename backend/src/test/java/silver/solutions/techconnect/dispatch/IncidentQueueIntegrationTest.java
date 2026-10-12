package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;

/** Logging an incident by hand, and the Overdue view being computed from the clock. */
class IncidentQueueIntegrationTest extends AbstractDispatchIntegrationTest {

    @Test
    void aControllerCanLogAnIncidentTakenByPhone() throws Exception {
        String controller = controllerToken();

        String body = mvc.perform(post("/v1/controller/incidents")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientName":"  Phone Call Co  ","siteAddress":"14 Main Rd, Rosebank",
                                 "siteLatitude":-26.1450,"siteLongitude":28.0430,
                                 "contactName":"Nomsa","contactPhone":"+27 82 555 0100",
                                 "issueType":"CCTV","priority":"HIGH",
                                 "description":"Cameras offline since the storm."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Phone Call Co"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.sla").value("4 hrs"))
                .andExpect(jsonPath("$.hasDispatch").value(false))
                .andExpect(jsonPath("$.ticketRef").value(org.hamcrest.Matchers.startsWith("INC-")))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        Incident saved = incidents.findById(id).orElseThrow();
        assertThat(saved.getFreshdeskTicketId()).isNull();
        assertThat(saved.getSlaDueAt()).isBetween(
                Instant.now().plus(Duration.ofHours(4)).minusSeconds(60),
                Instant.now().plus(Duration.ofHours(4)).plusSeconds(60));

        mvc.perform(get("/v1/controller/incidents").param("status", "NEW").param("size", "100")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(id.toString())));
    }

    @Test
    void anExplicitResponseTargetOverridesThePriorityDefault() throws Exception {
        String controller = controllerToken();

        mvc.perform(post("/v1/controller/incidents")
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientName":"Quick Co","priority":"LOW","slaHours":1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sla").value("1 hr"));
    }

    @Test
    void refusesAnIncidentThatIsMissingOrMalformed() throws Exception {
        String controller = controllerToken();

        String[] bad = {
                """
                {"priority":"HIGH"}""",
                """
                {"clientName":"   ","priority":"HIGH"}""",
                """
                {"clientName":"No Priority Co"}""",
                """
                {"clientName":"Half Pin Co","priority":"HIGH","siteLatitude":-26.1}""",
                """
                {"clientName":"Off Planet Co","priority":"HIGH","siteLatitude":95,"siteLongitude":28}""",
                """
                {"clientName":"Bad Phone Co","priority":"HIGH","contactPhone":"call me <script>"}""",
                """
                {"clientName":"Forever Co","priority":"HIGH","slaHours":9999}""",
        };
        for (String json : bad) {
            mvc.perform(post("/v1/controller/incidents")
                            .header("Authorization", "Bearer " + controller)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void onlyAControllerCanLogAnIncident() throws Exception {
        mvc.perform(post("/v1/controller/incidents")
                        .header("Authorization",
                                "Bearer " + tokenFor(technician("nolog",
                                        silver.solutions.techconnect.entity.TechnicianStatus.OFFLINE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientName":"Nope Co","priority":"LOW"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void overdueFollowsTheClockForEveryOpenIncidentNotJustOnesMarkedOverdue() throws Exception {
        String controller = controllerToken();
        Instant past = Instant.now().minusSeconds(900);
        int before = overdueCount(controller);

        Incident newAndLate = incident("NewLateCo", IncidentStatus.NEW, IncidentPriority.HIGH, past);
        Incident workedAndLate =
                incident("WorkedLateCo", IncidentStatus.IN_PROGRESS, IncidentPriority.MEDIUM, past);
        incident("ClosedLateCo", IncidentStatus.CLOSED, IncidentPriority.LOW, past);
        incident("OnTimeCo", IncidentStatus.NEW, IncidentPriority.LOW);

        // Searched by name so the assertions don't depend on what other tests left in the table.
        assertOverdueMatches(controller, "NewLateCo", newAndLate.getId());
        assertOverdueMatches(controller, "WorkedLateCo", workedAndLate.getId());
        assertOverdueMatches(controller, "ClosedLateCo", null);
        assertOverdueMatches(controller, "OnTimeCo", null);

        // The row flag, the tab and the KPI all agree on what overdue means: the two open late
        // incidents are counted, the closed one and the on-time one are not.
        mvc.perform(get("/v1/controller/incidents/{id}", workedAndLate.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.overdue").value(true));
        assertThat(overdueCount(controller)).isEqualTo(before + 2);
    }

    @Test
    void aManuallyLoggedIncidentIsFoundByTheReferenceShownInTheTable() throws Exception {
        String controller = controllerToken();
        Incident logged = incident("RefSearchCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        String shortId = logged.getId().toString().substring(0, 8);
        String reference = "INC-" + shortId.toUpperCase();

        // Exactly what the table shows, in any case, with the prefix optional, and as a fragment.
        assertSearchFinds(controller, reference, logged);
        assertSearchFinds(controller, reference.toLowerCase(), logged);
        assertSearchFinds(controller, shortId.toUpperCase(), logged);
        assertSearchFinds(controller, "  " + reference + "  ", logged);
        assertSearchFinds(controller, shortId.substring(0, 6), logged);

        // The table row itself carries that same reference.
        mvc.perform(get("/v1/controller/incidents/{id}", logged.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.ticketRef").value(reference));
    }

    @Test
    void anIncidentWithAFreshdeskTicketIsNotFoundByAReferenceItDoesNotShow() throws Exception {
        String controller = controllerToken();
        Incident ticketed = incident("TicketedCo", IncidentStatus.NEW, IncidentPriority.LOW);
        String ticketId = "FD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ticketed.setFreshdeskTicketId(ticketId);
        incidents.save(ticketed);

        assertSearchFinds(controller, ticketId, ticketed);
        assertSearchFinds(controller, ticketId.toLowerCase(), ticketed);
        // Its row shows the Freshdesk id, so the id-derived reference is not one it answers to.
        assertSearchFinds(controller, "INC-" + ticketed.getId().toString().substring(0, 8));
    }

    @Test
    void percentAndUnderscoreInTheSearchMatchOnlyThemselves() throws Exception {
        String controller = controllerToken();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Incident percent = incident("Pct%Co-" + tag, IncidentStatus.NEW, IncidentPriority.LOW);
        Incident percentLookalike = incident("PctXCo-" + tag, IncidentStatus.NEW, IncidentPriority.LOW);
        Incident underscore = incident("Und_Co-" + tag, IncidentStatus.NEW, IncidentPriority.LOW);
        Incident underscoreLookalike = incident("UndXCo-" + tag, IncidentStatus.NEW, IncidentPriority.LOW);
        Incident backslash = incident("Back\\Co-" + tag, IncidentStatus.NEW, IncidentPriority.LOW);

        // As wildcards "%" would match the look-alike too, and "_" any single character.
        assertSearchFinds(controller, "Pct%Co-" + tag, percent);
        assertSearchFinds(controller, "Und_Co-" + tag, underscore);
        assertSearchFinds(controller, "Back\\Co-" + tag, backslash);
        assertSearchFinds(controller, "%Co-" + tag, percent);
        assertSearchFinds(controller, "_Co-" + tag + "-nope");

        // A bare "%" is a literal percent sign, not "everything".
        String body = mvc.perform(get("/v1/controller/incidents")
                        .param("q", "%").param("size", "100")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(body, "$.content[*].clientName");
        assertThat(names).isNotEmpty().allMatch(name -> name.contains("%"));
        assertThat(names).contains(percent.getClientName())
                .doesNotContain(percentLookalike.getClientName(), underscoreLookalike.getClientName());
    }

    /** Searches the queue by {@code q} and expects exactly the given incidents, no others. */
    private void assertSearchFinds(String token, String q, Incident... expected) throws Exception {
        String body = mvc.perform(get("/v1/controller/incidents")
                        .param("q", q).param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).as("results for q=%s", q).containsExactlyInAnyOrder(
                Arrays.stream(expected).map(i -> i.getId().toString()).toArray(String[]::new));
    }

    private void assertOverdueMatches(String token, String name, UUID expected) throws Exception {
        var result = mvc.perform(get("/v1/controller/incidents")
                        .param("status", "OVERDUE").param("q", name)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        if (expected == null) {
            result.andExpect(jsonPath("$.content.length()").value(0));
        } else {
            result.andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(expected.toString()));
        }
    }

    private int overdueCount(String token) throws Exception {
        String body = mvc.perform(get("/v1/controller/incidents/kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.overdueCount");
    }
}
