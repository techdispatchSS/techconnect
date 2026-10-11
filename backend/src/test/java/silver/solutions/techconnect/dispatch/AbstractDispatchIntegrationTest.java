package silver.solutions.techconnect.dispatch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import silver.solutions.techconnect.AbstractPostgresIntegrationTest;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.DispatchResponseRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.service.NotificationService;

/**
 * Fixtures shared by the controller-dashboard integration tests. Tests built on this are not
 * {@code @Transactional} (they share one Postgres with every other class), so anything they
 * assert on must be created by the test itself rather than assumed to be absent.
 */
@AutoConfigureMockMvc
abstract class AbstractDispatchIntegrationTest extends AbstractPostgresIntegrationTest {

    static final String PASSWORD = "SharedPassw0rd!23";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired IncidentRepository incidents;
    @Autowired TechnicianProfileRepository technicianProfiles;
    @Autowired DispatchRepository dispatches;
    @Autowired DispatchResponseRepository dispatchResponses;
    @Autowired PasswordEncoder passwordEncoder;

    /** Mail is a side effect these tests assert on, not something to actually send. */
    @MockitoBean NotificationService notifications;

    static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@techconnect.test";
    }

    Incident incident(String clientName, IncidentStatus status, IncidentPriority priority) {
        return incident(clientName, status, priority, Instant.now().plusSeconds(14_400));
    }

    Incident incident(
            String clientName, IncidentStatus status, IncidentPriority priority, Instant slaDueAt) {
        Incident incident = new Incident();
        incident.setClientName(clientName);
        incident.setSiteAddress(clientName + " Office");
        incident.setPriority(priority);
        incident.setStatus(status);
        incident.setSla("4 hrs");
        incident.setSlaDueAt(slaDueAt);
        return incidents.save(incident);
    }

    User technician(String prefix, TechnicianStatus availability) {
        return technician(prefix, availability, UserStatus.ACTIVE);
    }

    User technician(String prefix, TechnicianStatus availability, UserStatus accountStatus) {
        User user = new User();
        user.setEmail(unique(prefix));
        user.setName("Test Technician " + prefix);
        user.setRole(UserRole.TECHNICIAN);
        user.setStatus(accountStatus);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = users.save(user);

        TechnicianProfile profile = TechnicianProfile.forUser(user.getId());
        profile.setStatus(availability);
        profile.setLastLatitude(new BigDecimal("-26.107600"));
        profile.setLastLongitude(new BigDecimal("28.056700"));
        technicianProfiles.save(profile);
        return user;
    }

    String controllerToken() throws Exception {
        User controller = new User();
        controller.setEmail(unique("controller"));
        controller.setName("Test Controller");
        controller.setRole(UserRole.CONTROLLER);
        controller.setStatus(UserStatus.ACTIVE);
        controller.setPasswordHash(passwordEncoder.encode(PASSWORD));
        users.save(controller);
        return tokenFor(controller.getEmail());
    }

    String tokenFor(User user) throws Exception {
        return tokenFor(user.getEmail());
    }

    private String tokenFor(String email) throws Exception {
        String body = mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /** Creates a dispatch as the Controller and returns its id. */
    UUID dispatch(String controllerToken, Incident incident, String type, User... invited)
            throws Exception {
        StringBuilder ids = new StringBuilder();
        for (User technician : invited) {
            ids.append(ids.isEmpty() ? "" : ",").append('"').append(technician.getId()).append('"');
        }
        String body = mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", "Bearer " + controllerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"%s","technicianIds":[%s]}
                                """.formatted(incident.getId(), type, ids)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
