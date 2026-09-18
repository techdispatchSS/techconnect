package silver.solutions.techconnect.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import silver.solutions.techconnect.AbstractPostgresIntegrationTest;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.service.NotificationService;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * Covers the admin portal's contract end to end (M-06) against a real PostgreSQL.
 *
 * <p>Tests are deliberately not {@code @Transactional}: the lockout counter is committed in
 * its own transaction by design, so a rolling-back test would hide the very behaviour being
 * asserted. Each test therefore uses unique email addresses instead of relying on rollback.
 */
@AutoConfigureMockMvc
class AdminPortalIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String MANAGER_PASSWORD = "ManagerPassw0rd!23";

    /** A fully valid structured address, reused wherever a test needs one but isn't itself
     * asserting on its contents. */
    private static final String VALID_ADDRESS = """
            {"street":"12 Long Street","suburb":"Gardens","city":"Cape Town",
             "province":"WESTERN_CAPE","postalCode":"8001"}""";

    /** What {@link #createTechnician} onboards every test technician with. */
    private static final String TECHNICIAN_ADDRESS = """
            {"street":"1 Test Street","suburb":"Umhlanga","city":"Durban",
             "province":"KWAZULU_NATAL","postalCode":"4001"}""";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private TechnicianProfileRepository technicianProfiles;
    @Autowired private PasswordEncoder passwordEncoder;

    /** Stubbed so tests never open an SMTP connection. */
    @MockitoBean private NotificationService notifications;

    @Test
    void onboardsATechnicianWithProfileAuditAndActivationLink() throws Exception {
        String managerToken = managerToken();
        String email = unique("thabo");

        String body = mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Thabo","lastName":"Mokoena","email":"%s","phone":"+27821234567",
                                 "address":%s,"role":"TECHNICIAN"}
                                """.formatted(email, VALID_ADDRESS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.user.technicianStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.user.address.street").value("12 Long Street"))
                .andExpect(jsonPath("$.user.address.suburb").value("Gardens"))
                .andExpect(jsonPath("$.user.address.city").value("Cape Town"))
                .andExpect(jsonPath("$.user.address.province").value("WESTERN_CAPE"))
                .andExpect(jsonPath("$.user.address.postalCode").value("8001"))
                // Returned to the Manager so onboarding works before any mail provider exists.
                .andExpect(jsonPath("$.activationUrl").value(
                        org.hamcrest.Matchers.containsString("/activate?token=")))
                .andReturn().getResponse().getContentAsString();

        UUID technicianId = UUID.fromString(jsonValue(body, "id"));

        assertThat(technicianProfiles.findById(technicianId))
                .as("a technician must get an availability profile for FR-04 dispatch")
                .isPresent()
                .get()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(TechnicianStatus.OFFLINE));

        mvc.perform(get("/v1/admin/audit")
                        .param("targetUserId", technicianId.toString())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("USER_CREATED"));
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCase() throws Exception {
        String managerToken = managerToken();
        String email = unique("dupe");

        createTechnician(managerToken, email);

        mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Impostor","lastName":"Test","email":"%s","address":%s,
                                 "role":"TECHNICIAN"}
                                """.formatted(email.toUpperCase(), VALID_ADDRESS)))
                .andExpect(status().isConflict());
    }

    @Test
    void createsAnotherManagerWhenAsked() throws Exception {
        mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Second","lastName":"Manager","email":"%s","address":%s,
                                 "role":"MANAGER"}
                                """.formatted(unique("manager2"), VALID_ADDRESS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("MANAGER"))
                // Managers have no dispatch availability, so no technician profile is created.
                .andExpect(jsonPath("$.user.technicianStatus").doesNotExist());
    }

    /** Address is required so distance-based job matching has an origin for every technician. */
    @Test
    void rejectsAUserWithoutAnAddress() throws Exception {
        mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"No","lastName":"Address","email":"%s","role":"TECHNICIAN"}
                                """.formatted(unique("noaddr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    /**
     * A Manager administers everyone but themselves; their own details live behind the
     * profile menu, so listing themselves as an administrable row is just noise.
     */
    @Test
    void excludesTheSignedInManagerFromTheUserList() throws Exception {
        String email = unique("selfexcluded");
        manager(email);
        String token = tokenFor(email, MANAGER_PASSWORD);

        mvc.perform(get("/v1/admin/users").param("q", "selfexcluded")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void refusesToEditYourOwnAccountThroughTheAdminEndpoints() throws Exception {
        String email = unique("selfedit");
        User self = manager(email);
        String token = tokenFor(email, MANAGER_PASSWORD);

        mvc.perform(put("/v1/admin/users/{id}", self.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","address":%s,"role":"TECHNICIAN"}"""
                                .formatted(VALID_ADDRESS)))
                .andExpect(status().isBadRequest());
    }

    /** Self-service profile edit — name and phone for everyone, address for Managers only. */
    @Test
    void letsAManagerEditTheirOwnProfileIncludingAddress() throws Exception {
        String email = unique("profile");
        User self = manager(email);
        String token = tokenFor(email, MANAGER_PASSWORD);

        mvc.perform(put("/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Manager","phone":"+27110000000",
                                 "address":%s}""".formatted(VALID_ADDRESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Manager"))
                .andExpect(jsonPath("$.address.street").value("12 Long Street"))
                .andExpect(jsonPath("$.address.province").value("WESTERN_CAPE"))
                .andExpect(jsonPath("$.canEditAddress").value(true));

        // A self-edit lands in the audit trail too (FR-09), distinguishable from a Manager
        // editing someone else by its own action rather than actor-equals-target.
        mvc.perform(get("/v1/admin/audit")
                        .param("targetUserId", self.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("SELF_PROFILE_UPDATED"))
                .andExpect(jsonPath("$.content[0].actorUserId").value(self.getId().toString()));
    }

    /** A no-op save (nothing actually changed) must not clutter the trail with an empty entry. */
    @Test
    void doesNotAuditAProfileSaveThatChangedNothing() throws Exception {
        String email = unique("noopprofile");
        User self = manager(email);
        String token = tokenFor(email, MANAGER_PASSWORD);

        mvc.perform(put("/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Manager","phone":null}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/admin/audit")
                        .param("targetUserId", self.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * A technician's address is the origin for distance-based dispatch, so letting them edit
     * it would let them quietly change which jobs they are offered.
     */
    @Test
    void ignoresAnAddressChangeFromANonManager() throws Exception {
        String managerToken = managerToken();
        String email = unique("techprofile");
        String technicianToken = activatedTechnicianToken(managerToken, email);

        mvc.perform(put("/v1/auth/me")
                        .header("Authorization", "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Tech","phone":"+27115555555",
                                 "address":%s}""".formatted(VALID_ADDRESS)))
                .andExpect(status().isOk())
                // The name change is honoured; the address change is silently ignored — it
                // still reads back as the one set at onboarding, not the WESTERN_CAPE one just
                // submitted.
                .andExpect(jsonPath("$.name").value("Renamed Tech"))
                .andExpect(jsonPath("$.address.street").value("1 Test Street"))
                .andExpect(jsonPath("$.address.province").value("KWAZULU_NATAL"))
                .andExpect(jsonPath("$.canEditAddress").value(false));
    }

    /** FR-01: a wrong-role token must be forbidden, not merely unauthenticated. */
    @Test
    void deniesAdminEndpointsToNonManagers() throws Exception {
        String managerToken = managerToken();
        String technicianToken = activatedTechnicianToken(managerToken, unique("rbac"));

        mvc.perform(get("/v1/admin/users").header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/v1/admin/users").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    /**
     * The reason {@code token_version} exists: a JWT is valid for 8 hours, so without an
     * explicit revocation check an offboarded technician would keep working until it expired.
     */
    @Test
    void offboardingRevokesAnAlreadyIssuedToken() throws Exception {
        String managerToken = managerToken();
        String email = unique("revoked");
        String technicianToken = activatedTechnicianToken(managerToken, email);
        UUID technicianId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/admin/users/{id}/deactivate", technicianId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        // Same token, not re-issued and not expired.
        mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isUnauthorized());

        assertThat(users.findById(technicianId).orElseThrow().getStatus())
                .as("offboarding must be a soft disable — the row is referenced by jobs and audit")
                .isEqualTo(UserStatus.DISABLED);
    }

    /**
     * Regression guard: the failure counter is written in its own transaction because the
     * caller's transaction is rolled back by the 401 it throws. Written naively, the count
     * never persists and the account never locks however many attempts are made.
     */
    @Test
    void locksAnAccountAfterRepeatedFailuresAndPersistsTheCount() throws Exception {
        String email = unique("locked");
        manager(email);

        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(login(email, "definitely-the-wrong-password"))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(users.findByEmailIgnoreCase(email).orElseThrow())
                .satisfies(user -> {
                    assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
                    assertThat(user.isLocked()).isTrue();
                });

        // The correct password is still refused while the lock stands — but since it proves
        // the caller owns the account, they are told why rather than left guessing.
        mvc.perform(login(email, MANAGER_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Too many failed")));

        // A wrong password against the same locked account reveals nothing.
        mvc.perform(login(email, "still-the-wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    /**
     * Regression guard: optional filters must not be bound as untyped nulls. Doing so makes
     * PostgreSQL fail with "function lower(bytea) does not exist" as soon as one is omitted.
     */
    @Test
    void listsUsersWithAnyCombinationOfOptionalFilters() throws Exception {
        String managerToken = managerToken();
        createTechnician(managerToken, unique("filter"));

        mvc.perform(get("/v1/admin/users").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/admin/users").param("role", "TECHNICIAN")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("TECHNICIAN"));

        mvc.perform(get("/v1/admin/users").param("status", "PENDING_ACTIVATION")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/admin/users").param("q", "Thabo")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/admin/users")
                        .param("role", "TECHNICIAN").param("status", "PENDING_ACTIVATION")
                        .param("q", "Technician").param("page", "0").param("size", "5")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    /** An activation link is single-use; replaying it must not let anyone reset the password. */
    @Test
    void refusesToReuseAnActivationToken() throws Exception {
        String managerToken = managerToken();
        String activationToken = tokenFromUrl(createTechnician(managerToken, unique("replay")));

        mvc.perform(post("/v1/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"TechPassw0rd!23"}
                                """.formatted(activationToken)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/v1/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"AttackerPassw0rd!"}
                                """.formatted(activationToken)))
                .andExpect(status().isBadRequest());
    }

    /**
     * A deactivated user who supplies the *correct* password is told why they cannot get in,
     * rather than being left to think they mistyped it.
     */
    @Test
    void tellsADeactivatedUserWhyWhenTheyGetTheirPasswordRight() throws Exception {
        String managerToken = managerToken();
        String email = unique("offboarded");
        activatedTechnicianToken(managerToken, email);
        UUID technicianId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        mvc.perform(post("/v1/admin/users/{id}/deactivate", technicianId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mvc.perform(login(email, "TechPassw0rd!23"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("deactivated")));
    }

    /**
     * The counterpart guarantee: the reason is disclosed only to someone who proved they own
     * the account. Without this, the login endpoint would become a staff directory — probe
     * addresses, learn who works here and who used to.
     */
    @Test
    void staysGenericForADeactivatedAccountWhenThePasswordIsWrong() throws Exception {
        String managerToken = managerToken();
        String email = unique("nodisclosure");
        activatedTechnicianToken(managerToken, email);
        UUID technicianId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        mvc.perform(post("/v1/admin/users/{id}/deactivate", technicianId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mvc.perform(login(email, "not-the-right-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));

        // An address that does not exist at all must be indistinguishable from the above.
        mvc.perform(login(unique("ghost"), "not-the-right-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void filtersTheAuditTrailByActionAndFreeText() throws Exception {
        String managerToken = managerToken();
        String email = unique("audited");
        createTechnician(managerToken, email);
        UUID technicianId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        mvc.perform(post("/v1/admin/users/{id}/deactivate", technicianId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        // Action filter narrows to the deactivation only.
        mvc.perform(get("/v1/admin/audit")
                        .param("targetUserId", technicianId.toString())
                        .param("action", "USER_DEACTIVATED")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("USER_DEACTIVATED"));

        // Free text reaches the target's email through the join.
        mvc.perform(get("/v1/admin/audit").param("q", email)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // A date range covering today includes them; one ending yesterday does not.
        mvc.perform(get("/v1/admin/audit")
                        .param("q", email)
                        .param("from", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("to", LocalDate.now(ZoneOffset.UTC).toString())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/v1/admin/audit")
                        .param("q", email)
                        .param("to", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // A term matching nothing returns nothing rather than falling back to everything.
        mvc.perform(get("/v1/admin/audit").param("q", "zzz-no-such-user-zzz")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** A 404 here would turn password recovery into a user-enumeration oracle. */
    @Test
    void forgotPasswordAcceptsUnknownAddressesWithoutRevealingThem() throws Exception {
        mvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"definitely-nobody@nowhere.test"}"""))
                .andExpect(status().isAccepted());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@techconnect.test";
    }

    private org.springframework.test.web.servlet.RequestBuilder login(
            String email, String password) {
        return post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private User manager(String email) {
        User manager = new User();
        manager.setEmail(email);
        manager.setName("Test Manager");
        manager.setRole(UserRole.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);
        manager.setPasswordHash(passwordEncoder.encode(MANAGER_PASSWORD));
        return users.save(manager);
    }

    private String managerToken() throws Exception {
        String email = unique("manager");
        manager(email);
        return tokenFor(email, MANAGER_PASSWORD);
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = mvc.perform(login(email, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonValue(body, "token");
    }

    /** @return the create-user response body, which carries the activation URL */
    private String createTechnician(String managerToken, String email) throws Exception {
        return mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"Technician","email":"%s",
                                 "address":%s,"role":"TECHNICIAN"}
                                """.formatted(email, TECHNICIAN_ADDRESS)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String activatedTechnicianToken(String managerToken, String email) throws Exception {
        String activationToken = tokenFromUrl(createTechnician(managerToken, email));

        mvc.perform(post("/v1/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"TechPassw0rd!23"}
                                """.formatted(activationToken)))
                .andExpect(status().isNoContent());

        String body = mvc.perform(login(email, "TechPassw0rd!23"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonValue(body, "token");
    }

    private static String tokenFromUrl(String createUserResponse) {
        String url = jsonValue(createUserResponse, "activationUrl");
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    /** Minimal extractor — avoids coupling the tests to a Jackson major version. */
    private static String jsonValue(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }
}
