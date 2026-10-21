package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.JobStatusHistoryRepository;
import silver.solutions.techconnect.service.JobService;

/**
 * A job from acceptance to the controller closing it (FR-05 to FR-07), the reports built on
 * those jobs, and the read-only technician roster.
 */
class JobFlowIntegrationTest extends AbstractDispatchIntegrationTest {

    @Autowired private JobRepository jobs;
    @Autowired private JobStatusHistoryRepository history;
    @Autowired private JobService jobService;

    /** A dispatch accepted by a fresh technician; returns the job it opened. */
    private Job acceptedJob(String controller, Incident incident, User technician) throws Exception {
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + tokenFor(technician)))
                .andExpect(status().isOk());
        return jobs.findByDispatchId(dispatchId).orElseThrow();
    }

    private void simulate(String controller, UUID jobId, String path, String body) throws Exception {
        var request = post("/v1/controller/simulation/jobs/{id}/" + path, jobId)
                .header("Authorization", "Bearer " + controller);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mvc.perform(request).andExpect(status().isOk());
    }

    private void moveTo(String controller, UUID jobId, String status) throws Exception {
        simulate(controller, jobId, "status", "{\"status\":\"%s\"}".formatted(status));
    }

    @Test
    void acceptingADispatchOpensAJobAndSpeaksForTheTechnician() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("OpensCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("opens", TechnicianStatus.AVAILABLE);

        Job job = acceptedJob(controller, incident, technician);

        assertThat(job.getStatus()).isEqualTo(JobStatus.EN_ROUTE);
        assertThat(job.getTechnicianId()).isEqualTo(technician.getId());
        assertThat(job.getIncidentId()).isEqualTo(incident.getId());
        assertThat(history.findByJobIdOrderByChangedAtDesc(job.getId())).singleElement().satisfies(h -> {
            assertThat(h.getStatus()).isEqualTo(JobStatus.EN_ROUTE);
            assertThat(h.getPreviousStatus()).isNull();
            assertThat(h.getChangedBy()).isEqualTo(technician.getId());
        });
        assertThat(technicianProfiles.findById(technician.getId())).get()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(TechnicianStatus.ON_JOB));

        mvc.perform(get("/v1/controller/jobs").param("q", "OpensCo")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].clientName").value("OpensCo"))
                .andExpect(jsonPath("$.content[0].status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.content[0].technicianName").value(technician.getName()));

        // The ticket's progress page names the job.
        mvc.perform(get("/v1/controller/incidents/{id}/progress", incident.getId())
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.jobStatus").value("EN_ROUTE"));
    }

    @Test
    void aRetriedAcceptDoesNotOpenASecondJob() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("RetryJobCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        User technician = technician("retryjob", TechnicianStatus.AVAILABLE);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);
        String token = tokenFor(technician);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        assertThat(jobs.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
    }

    @Test
    void aJobGoesFromEnRouteToClosedAndTheIncidentFollowsIt() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("FlowCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("flow", TechnicianStatus.AVAILABLE);
        Job job = acceptedJob(controller, incident, technician);
        UUID id = job.getId();

        moveTo(controller, id, "ON_SITE");
        moveTo(controller, id, "WORK_STARTED");
        simulate(controller, id, "status", "{\"status\":\"ON_HOLD\",\"reason\":\"Waiting for the client to open the comms room\"}");
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.ON_HOLD));

        moveTo(controller, id, "WORK_RESUMED");
        assertThat(incidents.findById(incident.getId())).get()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS));

        moveTo(controller, id, "COMPLETE");
        assertThat(technicianProfiles.findById(technician.getId())).get()
                .as("free again once the work is done")
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(TechnicianStatus.AVAILABLE));

        // Complete but not yet reported: it is still an active job, and cannot be closed.
        mvc.perform(post("/v1/controller/jobs/{id}/close", id)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        simulate(controller, id, "report", null);

        mvc.perform(get("/v1/controller/jobs").param("stage", "REVIEW").param("q", "FlowCo")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"));

        mvc.perform(get("/v1/controller/jobs/{id}", id)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClose").value(true))
                .andExpect(jsonPath("$.resolutionNotes").isNotEmpty())
                .andExpect(jsonPath("$.labourHours").value(2.5))
                .andExpect(jsonPath("$.parts[0].name").value("24-port PoE switch"))
                .andExpect(jsonPath("$.history[0].status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.history[-1:].status").value("SUBMITTED"));

        mvc.perform(post("/v1/controller/jobs/{id}/close", id)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingNotes\":\"Verified with the client.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("CLOSED"))
                .andExpect(jsonPath("$.closingNotes").value("Verified with the client."))
                .andExpect(jsonPath("$.canClose").value(false));

        Incident closed = incidents.findById(incident.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();

        // Closing twice is refused rather than quietly repeated.
        mvc.perform(post("/v1/controller/jobs/{id}/close", id)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        // Every step is on record, with who took it.
        assertThat(history.findByJobIdOrderByChangedAtDesc(id)).hasSize(8);
    }

    @Test
    void stepsOutOfOrderAreRefusedAndOnHoldNeedsAReason() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("OrderCo", IncidentStatus.NEW, IncidentPriority.LOW);
        User technician = technician("order", TechnicianStatus.AVAILABLE);
        UUID id = acceptedJob(controller, incident, technician).getId();

        // Cannot skip straight from en route to complete.
        mvc.perform(post("/v1/controller/simulation/jobs/{id}/status", id)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETE\"}"))
                .andExpect(status().isConflict());

        // Cannot report before the work is complete.
        mvc.perform(post("/v1/controller/simulation/jobs/{id}/report", id)
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isConflict());

        moveTo(controller, id, "ON_SITE");
        moveTo(controller, id, "WORK_STARTED");
        mvc.perform(post("/v1/controller/simulation/jobs/{id}/status", id)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_HOLD\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jobs.findById(id)).get()
                .satisfies(j -> assertThat(j.getStatus()).isEqualTo(JobStatus.WORK_STARTED));
    }

    @Test
    void anotherTechnicianCannotMoveSomeoneElsesJob() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("OwnerCo", IncidentStatus.NEW, IncidentPriority.MEDIUM);
        User owner = technician("owner", TechnicianStatus.AVAILABLE);
        User other = technician("other", TechnicianStatus.AVAILABLE);
        UUID id = acceptedJob(controller, incident, owner).getId();

        assertThatThrownBy(() -> jobService.advance(id, other.getId(), JobStatus.ON_SITE, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Job not found");
        assertThat(jobs.findById(id)).get()
                .satisfies(j -> assertThat(j.getStatus()).isEqualTo(JobStatus.EN_ROUTE));
    }

    @Test
    void theJobScreensAreForControllersOnly() throws Exception {
        User technician = technician("nosy", TechnicianStatus.AVAILABLE);
        String token = tokenFor(technician);

        for (String path : List.of("/v1/controller/jobs", "/v1/controller/jobs/counts",
                "/v1/controller/reports/summary", "/v1/controller/technicians")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theReportCountsTheWorkAndTheCsvKeepsFormulasInert() throws Exception {
        String controller = controllerToken();
        Incident incident = incident("=HYPERLINK(\"http://evil\")", IncidentStatus.NEW, IncidentPriority.HIGH);
        User technician = technician("report", TechnicianStatus.AVAILABLE);
        UUID id = acceptedJob(controller, incident, technician).getId();
        moveTo(controller, id, "ON_SITE");
        moveTo(controller, id, "WORK_STARTED");
        moveTo(controller, id, "COMPLETE");
        simulate(controller, id, "report", null);
        mvc.perform(post("/v1/controller/jobs/{id}/close", id)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        String today = LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String body = mvc.perform(get("/v1/controller/reports/summary")
                        .param("from", today).param("to", today).param("zone", "UTC")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("UTC"))
                .andReturn().getResponse().getContentAsString();

        // The database is shared with other tests, so this is "at least ours", not "exactly".
        assertThat((Integer) JsonPath.read(body, "$.incidents.created")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.incidents.closed")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.sla.withinSla")).isGreaterThanOrEqualTo(1);
        assertThat((Double) JsonPath.read(body, "$.sla.compliancePercent")).isBetween(0.0, 100.0);
        assertThat((Integer) JsonPath.read(body, "$.dispatching.accepts")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.jobs.closed")).isGreaterThanOrEqualTo(1);
        List<String> technicians = JsonPath.read(body, "$.byTechnician[*].name");
        assertThat(technicians).contains(technician.getName());
        assertThat((List<?>) JsonPath.read(body, "$.daily")).hasSize(1);
        assertThat((List<?>) JsonPath.read(body, "$.byPriority")).hasSize(3);

        byte[] csv = mvc.perform(get("/v1/controller/reports/incidents.csv")
                        .param("from", today).param("to", today).param("zone", "UTC")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Ticket,Client,Site,Priority,Status,Logged");
        // The formula is stored with a leading apostrophe, so a spreadsheet shows it as text.
        assertThat(text).contains("'=HYPERLINK").doesNotContain(",=HYPERLINK");
    }

    @Test
    void aBadRangeOrZoneIsRefused() throws Exception {
        String controller = controllerToken();

        mvc.perform(get("/v1/controller/reports/summary").param("zone", "Mars/Olympus")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/controller/reports/summary")
                        .param("from", "2026-05-10").param("to", "2026-05-01")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/controller/reports/summary")
                        .param("from", "2020-01-01").param("to", "2026-01-01")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theRosterShowsWhoIsFreeWhoIsBusyAndWhatTheyAreOn() throws Exception {
        String controller = controllerToken();
        User busy = technician("roster-busy", TechnicianStatus.AVAILABLE);
        User free = technician("roster-free", TechnicianStatus.AVAILABLE);
        User gone = technician("roster-gone", TechnicianStatus.OFFLINE, UserStatus.DISABLED);
        Incident incident = incident("RosterCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        acceptedJob(controller, incident, busy);

        mvc.perform(get("/v1/controller/technicians").param("q", "roster-")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='%s')].availability".formatted(busy.getId()))
                        .value("ON_JOB"))
                .andExpect(jsonPath("$.content[?(@.id=='%s')].currentJob.clientName".formatted(busy.getId()))
                        .value("RosterCo"))
                .andExpect(jsonPath("$.content[?(@.id=='%s')].availability".formatted(free.getId()))
                        .value("AVAILABLE"))
                // An offboarded technician is not on the roster unless asked for.
                .andExpect(jsonPath("$.content[?(@.id=='%s')]".formatted(gone.getId())).isEmpty());

        mvc.perform(get("/v1/controller/technicians").param("q", "roster-")
                        .param("includeDisabled", "true").param("size", "50")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.content[?(@.id=='%s')]".formatted(gone.getId())).isNotEmpty());

        mvc.perform(get("/v1/controller/technicians").param("availability", "ON_JOB")
                        .param("q", "roster-").param("size", "50")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(jsonPath("$.content[?(@.id=='%s')]".formatted(busy.getId())).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='%s')]".formatted(free.getId())).isEmpty());

        // Read-only: nothing on this path changes a technician.
        mvc.perform(post("/v1/controller/technicians").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + controller))
                .andExpect(status().isMethodNotAllowed());
    }
}
