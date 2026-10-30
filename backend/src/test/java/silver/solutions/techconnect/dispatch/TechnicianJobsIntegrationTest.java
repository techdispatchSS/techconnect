package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.repository.JobRepository;

/** A technician's own jobs and moving them along from their phone (FR-06, PRD §8.4). */
class TechnicianJobsIntegrationTest extends AbstractDispatchIntegrationTest {

    @Autowired private JobRepository jobs;

    private UUID acceptedJob(User technician, String client) throws Exception {
        Incident incident = incident(client, IncidentStatus.NEW, IncidentPriority.HIGH);
        incident.setContactName("Sipho");
        incident.setContactPhone("+27 82 000 0000");
        incidents.save(incident);
        UUID dispatchId = dispatch(controllerToken(), incident, "ASSIGN", technician);
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isOk());
        return jobs.findByDispatchId(dispatchId).orElseThrow().getId();
    }

    @Test
    void aTechnicianSeesTheirJobWithTheContactAndWhatTheyCanDoNext() throws Exception {
        User technician = technician("holder", TechnicianStatus.AVAILABLE);
        UUID jobId = acceptedJob(technician, "ListCo");
        String token = tokenFor(technician);

        mvc.perform(get("/v1/technician/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].clientName").value("ListCo"))
                .andExpect(jsonPath("$[0].status").value("EN_ROUTE"));

        job(token, jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail.job.status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.detail.contactName").value("Sipho"))
                .andExpect(jsonPath("$.detail.contactPhone").value("+27 82 000 0000"))
                .andExpect(jsonPath("$.detail.history", hasSize(1)))
                .andExpect(jsonPath("$.nextSteps", contains("ON_SITE")));
    }

    @Test
    void aTechnicianWorksAJobThroughToCompleteWithServerTimestamps() throws Exception {
        User technician = technician("worker", TechnicianStatus.AVAILABLE);
        UUID jobId = acceptedJob(technician, "WorkCo");
        String token = tokenFor(technician);

        move(token, jobId, "ON_SITE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail.job.arrivedAt").isNotEmpty())
                .andExpect(jsonPath("$.nextSteps", contains("WORK_STARTED")));
        move(token, jobId, "WORK_STARTED", null)
                .andExpect(jsonPath("$.nextSteps", contains("ON_HOLD", "COMPLETE")));
        move(token, jobId, "ON_HOLD", "Waiting for the client to open the server room")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextSteps", contains("WORK_RESUMED")));
        assertThat(incidents.findById(jobs.findById(jobId).orElseThrow().getIncidentId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.ON_HOLD));
        move(token, jobId, "WORK_RESUMED", null).andExpect(status().isOk());
        move(token, jobId, "COMPLETE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail.job.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.detail.history", hasSize(6)))
                .andExpect(jsonPath("$.detail.history[3].reason")
                        .value("Waiting for the client to open the server room"))
                .andExpect(jsonPath("$.nextSteps", hasSize(0)));

        Job done = jobs.findById(jobId).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(JobStatus.COMPLETE);
        assertThat(done.getArrivedAt()).isNotNull();
        assertThat(done.getStartedAt()).isNotNull();
        assertThat(technicianProfiles.findById(technician.getId()).orElseThrow().getStatus())
                .isEqualTo(TechnicianStatus.AVAILABLE);
    }

    @Test
    void aStepOutOfOrderOrAHoldWithoutAReasonIsRefused() throws Exception {
        User technician = technician("hasty", TechnicianStatus.AVAILABLE);
        UUID jobId = acceptedJob(technician, "HastyCo");
        String token = tokenFor(technician);

        move(token, jobId, "COMPLETE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A job that is en route cannot move to complete."));
        move(token, jobId, "SUBMITTED", null).andExpect(status().isConflict());
        move(token, jobId, "ON_SITE", null).andExpect(status().isOk());
        move(token, jobId, "WORK_STARTED", null).andExpect(status().isOk());
        move(token, jobId, "ON_HOLD", "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Say why the job is being put on hold."));
        mvc.perform(put("/v1/technician/jobs/{id}/status", jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void someoneElsesJobIsNotFound() throws Exception {
        User holder = technician("owner", TechnicianStatus.AVAILABLE);
        User other = technician("nosy", TechnicianStatus.AVAILABLE);
        UUID jobId = acceptedJob(holder, "PrivateCo");
        String token = tokenFor(other);

        job(token, jobId).andExpect(status().isNotFound());
        move(token, jobId, "ON_SITE", null).andExpect(status().isNotFound());
        mvc.perform(get("/v1/technician/jobs").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.EN_ROUTE);
    }

    @Test
    void controllersUseTheirOwnJobScreens() throws Exception {
        mvc.perform(get("/v1/technician/jobs").header("Authorization", "Bearer " + controllerToken()))
                .andExpect(status().isForbidden());
    }

    private ResultActions job(String token, UUID jobId) throws Exception {
        return mvc.perform(get("/v1/technician/jobs/{id}", jobId).header("Authorization", "Bearer " + token));
    }

    private ResultActions move(String token, UUID jobId, String status, String reason) throws Exception {
        String body = reason == null
                ? "{\"status\":\"%s\"}".formatted(status)
                : "{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(status, reason);
        return mvc.perform(put("/v1/technician/jobs/{id}/status", jobId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
