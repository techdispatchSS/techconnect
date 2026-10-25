package silver.solutions.techconnect.dispatch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.repository.JobRepository;

/**
 * What an incident's Freshdesk ticket is told as the work moves along. The tests run in demo
 * mode (see AbstractPostgresIntegrationTest), the default: every update is recorded and shown,
 * none is sent.
 */
class FreshdeskUpdatesIntegrationTest extends AbstractDispatchIntegrationTest {

    @Autowired private JobRepository jobs;

    private Incident ticketed(String client) {
        Incident incident = incident(client, IncidentStatus.NEW, IncidentPriority.HIGH);
        // Numeric like a real Freshdesk id, and unique across the shared test database.
        incident.setFreshdeskTicketId(Long.toString(System.nanoTime()));
        return incidents.save(incident);
    }

    private void simulate(String controller, UUID jobId, String path, String body) throws Exception {
        var request = post("/v1/controller/simulation/jobs/{id}/" + path, jobId)
                .header("Authorization", "Bearer " + controller);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mvc.perform(request).andExpect(status().isOk());
    }

    @Test
    void everyStepOfAJobBecomesADemoUpdateOnTheTicket() throws Exception {
        String controller = controllerToken();
        Incident incident = ticketed("UpdatesCo");
        User technician = technician("updates", TechnicianStatus.AVAILABLE);

        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isOk());
        Job job = jobs.findByDispatchId(dispatchId).orElseThrow();

        for (String step : new String[] {"ON_SITE", "WORK_STARTED"}) {
            simulate(controller, job.getId(), "status", "{\"status\":\"%s\"}".formatted(step));
        }
        simulate(controller, job.getId(), "status", "{\"status\":\"ON_HOLD\",\"reason\":\"Waiting for a part\"}");
        simulate(controller, job.getId(), "status", "{\"status\":\"WORK_RESUMED\"}");
        simulate(controller, job.getId(), "status", "{\"status\":\"COMPLETE\"}");
        simulate(controller, job.getId(), "report", null);
        mvc.perform(post("/v1/controller/jobs/{id}/close", job.getId())
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingNotes\":\"Verified with the client.\"}"))
                .andExpect(status().isOk());

        String name = technician.getName();
        mvc.perform(get("/v1/controller/incidents/{id}/freshdesk-updates", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[*].delivery", Matchers.everyItem(Matchers.is("DEMO"))))
                .andExpect(jsonPath("$[*].event").value(Matchers.contains(
                        "DISPATCH_SENT", "DISPATCH_ACCEPTED", "JOB_PROGRESS", "JOB_PROGRESS", "JOB_PROGRESS",
                        "JOB_PROGRESS", "JOB_PROGRESS", "REPORT_SUBMITTED", "JOB_CLOSED", "JOB_CLOSED")))
                .andExpect(jsonPath("$[0].body").value("TechConnect assigned this job to " + name + "."))
                .andExpect(jsonPath("$[1].body").value(name + " accepted the job and is on the way to site."))
                .andExpect(jsonPath("$[4].body").value("Work on hold: Waiting for a part"))
                .andExpect(jsonPath("$[7].body").value(Matchers.startsWith("Technician's report from " + name)))
                .andExpect(jsonPath("$[8].action").value("NOTE"))
                .andExpect(jsonPath("$[8].body").value(Matchers.endsWith(": Verified with the client.")))
                .andExpect(jsonPath("$[9].action").value("RESOLVE"))
                .andExpect(jsonPath("$[9].body").doesNotExist())
                .andExpect(jsonPath("$[9].sentAt").doesNotExist());
    }

    @Test
    void aDispatchNobodyTakesIsNotedOnTheTicket() throws Exception {
        String controller = controllerToken();
        Incident incident = ticketed("LapsedCo");
        User technician = technician("lapsed", TechnicianStatus.AVAILABLE);

        UUID dispatchId = dispatch(controller, incident, "BROADCAST", technician);
        // The only technician asked declines, which lapses the dispatch at once.
        mvc.perform(post("/v1/technician/dispatches/{id}/decline", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/controller/incidents/{id}/freshdesk-updates", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$[*].event").value(Matchers.contains("DISPATCH_SENT", "DISPATCH_EXPIRED")))
                .andExpect(jsonPath("$[0].body").value(Matchers.containsString("1 technician (")));
    }

    @Test
    void anIncidentWithoutATicketHasNothingToTell() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("NoTicketCo", IncidentStatus.NEW, IncidentPriority.LOW);
        dispatch(controller, incident, "ASSIGN", technician("noticket", TechnicianStatus.AVAILABLE));

        mvc.perform(get("/v1/controller/incidents/{id}/freshdesk-updates", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theHeaderStatusSaysUpdatesAreDemo() throws Exception {
        mvc.perform(get("/v1/controller/integrations/freshdesk")
                        .header("Authorization", "Bearer " + controllerToken()))
                .andExpect(jsonPath("$.pushMode").value("DEMO"));
    }

    @Test
    void anUnknownIncidentIsNotFound() throws Exception {
        mvc.perform(get("/v1/controller/incidents/{id}/freshdesk-updates", UUID.randomUUID())
                        .header("Authorization", "Bearer " + controllerToken()))
                .andExpect(status().isNotFound());
    }
}
