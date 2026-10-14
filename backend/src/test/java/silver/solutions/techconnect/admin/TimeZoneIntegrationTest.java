package silver.solutions.techconnect.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import silver.solutions.techconnect.AbstractPostgresIntegrationTest;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.service.NotificationService;

/** A person's browser reports the zone they are in, and emails to them are said in it. */
@AutoConfigureMockMvc
class TimeZoneIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "TechnicianPassw0rd!23";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private NotificationService notifications;

    private User technician() {
        User user = new User();
        user.setEmail("tz-" + UUID.randomUUID() + "@techconnect.test");
        user.setName("Zanele Dube");
        user.setRole(UserRole.TECHNICIAN);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.save(user);
    }

    private String tokenFor(User user) throws Exception {
        String body = mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String marker = "\"token\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    void aNewPersonHasNoZoneUntilTheirBrowserReportsOne() throws Exception {
        User user = technician();
        String token = tokenFor(user);

        mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").doesNotExist());

        mvc.perform(put("/v1/auth/me/time-zone")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"Africa/Nairobi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Africa/Nairobi"));

        assertThat(users.findById(user.getId()).orElseThrow().getTimeZone())
                .isEqualTo("Africa/Nairobi");
        mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.timeZone").value("Africa/Nairobi"));
    }

    @Test
    void theZoneFollowsThePersonWhenTheyMove() throws Exception {
        User user = technician();
        String token = tokenFor(user);

        for (String zone : new String[] {"Africa/Lagos", "Africa/Johannesburg"}) {
            mvc.perform(put("/v1/auth/me/time-zone")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timeZone\":\"%s\"}".formatted(zone)))
                    .andExpect(status().isOk());
            assertThat(users.findById(user.getId()).orElseThrow().getTimeZone()).isEqualTo(zone);
        }
    }

    @Test
    void anythingThatIsNotARealZoneIsRefused() throws Exception {
        User user = technician();
        String token = tokenFor(user);

        for (String bad : new String[] {"Mars/Olympus", "UTC+2", "<script>", "  ", "x".repeat(65)}) {
            mvc.perform(put("/v1/auth/me/time-zone")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timeZone\":\"%s\"}".formatted(bad)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.findById(user.getId()).orElseThrow().getTimeZone()).isNull();
    }

    @Test
    void youNeedToBeSignedInToSetOne() throws Exception {
        mvc.perform(put("/v1/auth/me/time-zone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"Africa/Nairobi\"}"))
                .andExpect(status().isUnauthorized());
    }
}
