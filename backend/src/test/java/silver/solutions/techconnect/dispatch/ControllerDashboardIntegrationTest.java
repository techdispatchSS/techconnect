package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import silver.solutions.techconnect.AbstractPostgresIntegrationTest;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianSkill;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.DispatchResponseRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.TechnicianSkillRepository;
import silver.solutions.techconnect.repository.UserRepository;

/** Covers the controller dashboard's contract end to end (FR-02–FR-04) against a real PostgreSQL. */
@AutoConfigureMockMvc
class ControllerDashboardIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CONTROLLER_PASSWORD = "ControllerPassw0rd!23";
    private static final String TECHNICIAN_PASSWORD = "TechnicianPassw0rd!23";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private IncidentRepository incidents;
    @Autowired private TechnicianProfileRepository technicianProfiles;
    @Autowired private TechnicianSkillRepository technicianSkills;
    @Autowired private DispatchRepository dispatches;
    @Autowired private DispatchResponseRepository dispatchResponses;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * Tests in this class are deliberately not {@code @Transactional} (see
     * {@code AdminPortalIntegrationTest}'s own note) and share one static Postgres container,
     * so other tests' incidents are still there when this one runs. KPIs are therefore
     * asserted as *deltas* around this test's own fixtures, not as absolute counts.
     */
    @Test
    void listsIncidentsByTabAndComputesKpisConsistently() throws Exception {
        String token = controllerToken();

        int[] before = fetchKpis(token);

        Incident overdue = incident("OverdueCo", IncidentStatus.OVERDUE, IncidentPriority.HIGH);
        incident("FreshCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        incident("BusyCo", IncidentStatus.IN_PROGRESS, IncidentPriority.LOW);

        int[] after = fetchKpis(token);
        assertThat(after[0]).as("newCount").isEqualTo(before[0] + 1);
        assertThat(after[1]).as("unassignedCount").isEqualTo(before[1] + 3);
        assertThat(after[2]).as("inProgressCount").isEqualTo(before[2] + 1);
        assertThat(after[3]).as("overdueCount").isEqualTo(before[3] + 1);

        mvc.perform(get("/v1/controller/incidents").param("status", "OVERDUE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id",
                        org.hamcrest.Matchers.hasItem(overdue.getId().toString())))
                .andExpect(jsonPath("$.content[?(@.id=='" + overdue.getId() + "')].overdue")
                        .value(true));

        mvc.perform(get("/v1/controller/incidents").param("unassigned", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id",
                        org.hamcrest.Matchers.hasItem(overdue.getId().toString())));
    }

    /** @return {newCount, unassignedCount, inProgressCount, overdueCount} */
    private int[] fetchKpis(String token) throws Exception {
        String body = mvc.perform(get("/v1/controller/incidents/kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new int[] {
                jsonInt(body, "newCount"), jsonInt(body, "unassignedCount"),
                jsonInt(body, "inProgressCount"), jsonInt(body, "overdueCount"),
        };
    }

    @Test
    void ranksTechniciansBySkillMatchAheadOfAnUnskilledAvailableTechnician() throws Exception {
        String controllerToken = controllerToken();
        Incident site = incident("ABC Holdings", IncidentStatus.NEW, IncidentPriority.HIGH);

        User skilled = technician("skilled", TechnicianStatus.AVAILABLE);
        technicianSkills.save(new TechnicianSkill(skilled.getId(), "Networking"));
        User unskilled = technician("unskilled", TechnicianStatus.AVAILABLE);

        String body = mvc.perform(get("/v1/controller/incidents/{id}/technicians", site.getId())
                        .param("skills", "Networking")
                        .header("Authorization", "Bearer " + controllerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(skilled.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(unskilled.getId().toString());
    }

    @Test
    void createsABroadcastDispatchAndMovesTheIncidentInProgress() throws Exception {
        String controllerToken = controllerToken();
        Incident site = incident("XYZ Group", IncidentStatus.NEW, IncidentPriority.HIGH);
        User tech1 = technician("bcast1", TechnicianStatus.AVAILABLE);
        User tech2 = technician("bcast2", TechnicianStatus.AVAILABLE);

        String body = mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controllerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"BROADCAST","jobType":"Incident",
                                 "requiredSkills":["Networking"],"requiredCertifications":[],
                                 "slaResponse":"4 hrs","siteContact":"John Smith",
                                 "notesForTechnician":"Core switch offline.",
                                 "technicianIds":["%s","%s"]}
                                """.formatted(site.getId(), tech1.getId(), tech2.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispatchType").value("BROADCAST"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        UUID dispatchId = UUID.fromString(jsonValue(body, "id"));

        assertThat(dispatches.findById(dispatchId)).isPresent()
                .get().satisfies(d -> assertThat(d.getRequiredSkills()).isEqualTo("Networking"));
        assertThat(dispatchResponses.findByDispatchId(dispatchId)).hasSize(2);
        assertThat(incidents.findById(site.getId())).isPresent()
                .get().satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS));
    }

    @Test
    void refusesADispatchWithNoTechniciansSelected() throws Exception {
        String controllerToken = controllerToken();
        Incident site = incident("NoTechCo", IncidentStatus.NEW, IncidentPriority.LOW);

        mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controllerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"BROADCAST","technicianIds":[]}
                                """.formatted(site.getId())))
                .andExpect(status().isBadRequest());
    }

    /** FR-02/FR-03/FR-04: a wrong-role token must be forbidden, not merely unauthenticated. */
    @Test
    void deniesControllerEndpointsToNonControllers() throws Exception {
        String controllerToken = controllerToken();
        String technicianToken = technicianToken("rbac");

        mvc.perform(get("/v1/controller/incidents").header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/controller/incidents"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/v1/controller/incidents").header("Authorization", "Bearer " + controllerToken))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@techconnect.test";
    }

    private Incident incident(String clientName, IncidentStatus status, IncidentPriority priority) {
        Incident incident = new Incident();
        incident.setClientName(clientName);
        incident.setSiteAddress(clientName + " Office");
        incident.setPriority(priority);
        incident.setStatus(status);
        incident.setSla("4 hrs");
        incident.setSlaDueAt(status == IncidentStatus.OVERDUE
                ? Instant.now().minusSeconds(600)
                : Instant.now().plusSeconds(14_400));
        return incidents.save(incident);
    }

    private User technician(String prefix, TechnicianStatus status) {
        User user = new User();
        user.setEmail(unique(prefix));
        user.setName("Test Technician " + prefix);
        user.setRole(UserRole.TECHNICIAN);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(TECHNICIAN_PASSWORD));
        user = users.save(user);

        TechnicianProfile profile = TechnicianProfile.forUser(user.getId());
        profile.setStatus(status);
        profile.setLastLatitude(new BigDecimal("-26.107600"));
        profile.setLastLongitude(new BigDecimal("28.056700"));
        technicianProfiles.save(profile);

        return user;
    }

    private String controllerToken() throws Exception {
        String email = unique("controller");
        User controller = new User();
        controller.setEmail(email);
        controller.setName("Test Controller");
        controller.setRole(UserRole.CONTROLLER);
        controller.setStatus(UserStatus.ACTIVE);
        controller.setPasswordHash(passwordEncoder.encode(CONTROLLER_PASSWORD));
        users.save(controller);
        return tokenFor(email, CONTROLLER_PASSWORD);
    }

    private String technicianToken(String prefix) throws Exception {
        User tech = technician(prefix, TechnicianStatus.OFFLINE);
        return tokenFor(tech.getEmail(), TECHNICIAN_PASSWORD);
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonValue(body, "token");
    }

    /** Minimal extractor — avoids coupling the tests to a Jackson major version. */
    private static String jsonValue(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private static int jsonInt(String json, String field) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"" + field + "\":(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No integer field '" + field + "' in: " + json);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
