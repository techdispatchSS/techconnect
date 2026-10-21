package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;

/**
 * Two controllers must not both dispatch the same incident to different technicians. The hold is
 * taken when a controller opens the dispatch screen, renewed while it stays open, and lapses on
 * its own; creating a dispatch refuses while someone else holds it.
 */
class IncidentLockIntegrationTest extends AbstractDispatchIntegrationTest {

    private String auth(String token) {
        return "Bearer " + token;
    }

    private String lockBody(String token, UUID incident, int expected) throws Exception {
        return mvc.perform(post("/v1/controller/incidents/{id}/lock", incident)
                        .header("Authorization", auth(token)))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    private int createDispatchAs(String token, Incident incident, User technician) throws Exception {
        return mvc.perform(post("/v1/controller/dispatches")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":"%s","dispatchType":"ASSIGN","technicianIds":["%s"]}
                                """.formatted(incident.getId(), technician.getId())))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void openingAnIncidentTakesItAndTheQueueShowsWhoHasIt() throws Exception {
        String ann = controllerToken();
        Incident incident = incident("OpensTheLockCo", IncidentStatus.NEW, IncidentPriority.HIGH);

        String body = lockBody(ann, incident.getId(), 200);
        assertThat((String) JsonPath.read(body, "$.byName")).isEqualTo("Test Controller");
        assertThat(Instant.parse(JsonPath.read(body, "$.expiresAt"))).isAfter(Instant.now());

        String holder = JsonPath.read(body, "$.byId");
        mvc.perform(get("/v1/controller/incidents/{id}", incident.getId())
                        .header("Authorization", auth(controllerToken())))
                .andExpect(jsonPath("$.lock.byId").value(holder))
                .andExpect(jsonPath("$.lock.byName").value("Test Controller"));

        // The list says so too, which is what the queue reads.
        mvc.perform(get("/v1/controller/incidents").param("q", "OpensTheLockCo")
                        .header("Authorization", auth(controllerToken())))
                .andExpect(jsonPath("$.content[0].lock.byId").value(holder));
    }

    @Test
    void anotherControllerCannotTakeAnIncidentThatIsBeingDispatched() throws Exception {
        String ann = controllerToken();
        String ben = controllerToken();
        Incident incident = incident("TakenCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        lockBody(ann, incident.getId(), 200);

        String refusal = lockBody(ben, incident.getId(), 409);

        assertThat((String) JsonPath.read(refusal, "$.error"))
                .contains("is dispatching this incident right now");
    }

    @Test
    void theHolderRenewsItsHoldRatherThanBeingRefused() throws Exception {
        String ann = controllerToken();
        Incident incident = incident("RenewCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        Instant first = Instant.parse(JsonPath.read(lockBody(ann, incident.getId(), 200), "$.expiresAt"));
        // Pretend most of the hold has gone.
        Incident held = incidents.findById(incident.getId()).orElseThrow();
        held.setLockExpiresAt(Instant.now().plusSeconds(5));
        incidents.save(held);

        Instant renewed = Instant.parse(JsonPath.read(lockBody(ann, incident.getId(), 200), "$.expiresAt"));

        assertThat(renewed).isAfter(Instant.now().plusSeconds(60));
        assertThat(first).isAfter(Instant.now());
    }

    @Test
    void aHoldThatHasLapsedIsFreeForSomeoneElseToTake() throws Exception {
        String ann = controllerToken();
        String ben = controllerToken();
        Incident incident = incident("LapseCo", IncidentStatus.NEW, IncidentPriority.LOW);
        lockBody(ann, incident.getId(), 200);

        // Ann closed her tab two minutes ago.
        Incident held = incidents.findById(incident.getId()).orElseThrow();
        held.setLockExpiresAt(Instant.now().minusSeconds(1));
        incidents.save(held);

        lockBody(ben, incident.getId(), 200);
        mvc.perform(get("/v1/controller/incidents/{id}", incident.getId())
                        .header("Authorization", auth(ann)))
                .andExpect(jsonPath("$.lock").isNotEmpty());
    }

    @Test
    void releasingFreesItButOnlyForTheHolder() throws Exception {
        String ann = controllerToken();
        String ben = controllerToken();
        Incident incident = incident("ReleaseCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        lockBody(ann, incident.getId(), 200);

        // Ben cannot let go of Ann's hold.
        mvc.perform(delete("/v1/controller/incidents/{id}/lock", incident.getId())
                        .header("Authorization", auth(ben)))
                .andExpect(status().isNoContent());
        lockBody(ben, incident.getId(), 409);

        mvc.perform(delete("/v1/controller/incidents/{id}/lock", incident.getId())
                        .header("Authorization", auth(ann)))
                .andExpect(status().isNoContent());
        lockBody(ben, incident.getId(), 200);
    }

    @Test
    void aSecondControllerCannotDispatchWhileTheFirstIsAtIt() throws Exception {
        String ann = controllerToken();
        String ben = controllerToken();
        Incident incident = incident("RaceCo2", IncidentStatus.NEW, IncidentPriority.HIGH);
        User first = technician("lock-first", TechnicianStatus.AVAILABLE);
        User second = technician("lock-second", TechnicianStatus.AVAILABLE);
        lockBody(ann, incident.getId(), 200);

        assertThat(createDispatchAs(ben, incident, second)).isEqualTo(409);
        assertThat(dispatches.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();

        // Ann, who holds it, can; and doing so ends the hold.
        assertThat(createDispatchAs(ann, incident, first)).isEqualTo(201);
        assertThat(dispatches.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getLockedBy()).isNull());
    }

    @Test
    void dispatchingWithoutOpeningTheScreenStillWorksWhenNobodyHoldsIt() throws Exception {
        String ann = controllerToken();
        Incident incident = incident("NoLockCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        User technician = technician("lock-none", TechnicianStatus.AVAILABLE);

        assertThat(createDispatchAs(ann, incident, technician)).isEqualTo(201);
    }

    @Test
    void anExpiredHoldDoesNotBlockADispatch() throws Exception {
        String ann = controllerToken();
        String ben = controllerToken();
        Incident incident = incident("ExpiredCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        User technician = technician("lock-expired", TechnicianStatus.AVAILABLE);
        lockBody(ann, incident.getId(), 200);
        Incident held = incidents.findById(incident.getId()).orElseThrow();
        held.setLockExpiresAt(Instant.now().minusSeconds(1));
        incidents.save(held);

        assertThat(createDispatchAs(ben, incident, technician)).isEqualTo(201);
    }

    @Test
    void aClosedOrAlreadyDispatchedIncidentCannotBeTaken() throws Exception {
        String ann = controllerToken();
        Incident closed = incident("ClosedCo", IncidentStatus.CLOSED, IncidentPriority.LOW);
        lockBody(ann, closed.getId(), 409);

        Incident dispatched = incident("DispatchedCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("lock-dispatched", TechnicianStatus.AVAILABLE);
        dispatch(ann, dispatched, "ASSIGN", technician);
        lockBody(controllerToken(), dispatched.getId(), 409);
    }

    @Test
    void controllersOpeningTheSameIncidentAtTheSameInstantResolveToOneHolder() throws Exception {
        Incident incident = incident("SimultaneousCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tokens.add(controllerToken());
        }

        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : tokens) {
                Callable<Integer> open = () -> mvc.perform(
                                post("/v1/controller/incidents/{id}/lock", incident.getId())
                                        .header("Authorization", auth(token)))
                        .andReturn().getResponse().getStatus();
                results.add(pool.submit(open));
            }
            int won = 0;
            int refused = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                if (code == 200) {
                    won++;
                } else if (code == 409) {
                    refused++;
                }
            }
            assertThat(won).as("exactly one holder").isEqualTo(1);
            assertThat(refused).isEqualTo(tokens.size() - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void onlyControllersCanTakeAnIncident() throws Exception {
        Incident incident = incident("GuardedCo", IncidentStatus.NEW, IncidentPriority.LOW);
        User technician = technician("lock-guard", TechnicianStatus.AVAILABLE);

        mvc.perform(post("/v1/controller/incidents/{id}/lock", incident.getId())
                        .header("Authorization", auth(tokenFor(technician))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/controller/incidents/{id}/lock", incident.getId()))
                .andExpect(status().isUnauthorized());
    }
}
