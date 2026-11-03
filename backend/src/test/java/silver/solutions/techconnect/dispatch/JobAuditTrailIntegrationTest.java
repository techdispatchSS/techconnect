package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.JobStatusHistoryRepository;

/** The immutable log of a job's status changes (FR-09, §9.5). */
class JobAuditTrailIntegrationTest extends AbstractDispatchIntegrationTest {

    private static final String REPORT = """
            {"resolutionNotes":"Replaced it.","rootCause":"Failed switch.","labourHours":1}""";

    @Autowired private JobRepository jobs;
    @Autowired private JdbcTemplate jdbc;

    private String controller;
    private User technician;

    /** A job taken from accept to closed, so the trail has every kind of step in it. */
    private UUID closedJob() throws Exception {
        controller = controllerToken();
        technician = technician("audited", TechnicianStatus.AVAILABLE);
        String token = tokenFor(technician);
        UUID dispatchId = dispatch(controller,
                incident("AuditCo", IncidentStatus.NEW, IncidentPriority.HIGH), "ASSIGN", technician);
        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        UUID jobId = jobs.findByDispatchId(dispatchId).orElseThrow().getId();
        for (String step : new String[] {"ON_SITE", "WORK_STARTED", "COMPLETE"}) {
            mvc.perform(put("/v1/technician/jobs/{id}/status", jobId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"%s\"}".formatted(step)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/v1/technician/jobs/{id}/submit", jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/controller/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + controller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingNotes\":\"All good.\"}"))
                .andExpect(status().isOk());
        return jobId;
    }

    @Test
    void everyChangeIsLoggedWithWhoWhenAndFromWhatIncludingTheClose() throws Exception {
        UUID jobId = closedJob();

        ResultActions trail = history("/v1/controller/jobs/{id}/history", controller, jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].status").value("EN_ROUTE"))
                .andExpect(jsonPath("$[0].previousStatus").value(nullValue()))
                .andExpect(jsonPath("$[1].status").value("ON_SITE"))
                .andExpect(jsonPath("$[1].previousStatus").value("EN_ROUTE"))
                .andExpect(jsonPath("$[1].changedById").value(technician.getId().toString()))
                .andExpect(jsonPath("$[1].changedByName").value(technician.getName()))
                .andExpect(jsonPath("$[4].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[5].status").value("CLOSED"))
                .andExpect(jsonPath("$[5].previousStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[5].reason").value("All good."))
                .andExpect(jsonPath("$[5].changedByName").value("Test Controller"));
        for (int i = 0; i < 6; i++) {
            trail.andExpect(jsonPath("$[%d].changedAt".formatted(i)).isNotEmpty());
        }
    }

    @Test
    void controllersAndManagersCanReadItAndNobodyElse() throws Exception {
        UUID jobId = closedJob();

        history("/v1/controller/jobs/{id}/history", controller, jobId).andExpect(status().isOk());
        history("/v1/admin/jobs/{id}/history", managerToken(), jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));

        String technicianToken = tokenFor(technician);
        history("/v1/controller/jobs/{id}/history", technicianToken, jobId).andExpect(status().isForbidden());
        history("/v1/admin/jobs/{id}/history", technicianToken, jobId).andExpect(status().isForbidden());
        history("/v1/admin/jobs/{id}/history", controller, jobId).andExpect(status().isForbidden());
    }

    @Test
    void aJobThatDoesNotExistIsNotFound() throws Exception {
        history("/v1/admin/jobs/{id}/history", managerToken(), UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    @Test
    void theDatabaseRefusesToChangeOrRemoveAnEntry() throws Exception {
        UUID jobId = closedJob();
        Integer before = rows(jobId);

        assertThatThrownBy(() -> jdbc.update(
                        "update job_status_history set reason = 'tampered' where job_id = ?", jobId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from job_status_history where job_id = ?", jobId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate job_status_history cascade"))
                .hasMessageContaining("append-only");

        assertThat(rows(jobId)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                        "select count(*) from job_status_history where job_id = ? and reason = 'tampered'",
                        Integer.class, jobId))
                .isZero();
    }

    @Test
    void theRepositoryOffersNoWayToDelete() {
        assertThat(Arrays.stream(JobStatusHistoryRepository.class.getMethods()).map(Method::getName))
                .containsOnly("save", "findByJobIdOrderByChangedAtDesc");
    }

    private Integer rows(UUID jobId) {
        return jdbc.queryForObject(
                "select count(*) from job_status_history where job_id = ?", Integer.class, jobId);
    }

    private ResultActions history(String path, String token, UUID jobId) throws Exception {
        return mvc.perform(get(path, jobId).header("Authorization", "Bearer " + token));
    }

    private String managerToken() throws Exception {
        User manager = new User();
        manager.setEmail(unique("manager"));
        manager.setName("Test Manager");
        manager.setRole(UserRole.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);
        manager.setPasswordHash(passwordEncoder.encode(PASSWORD));
        users.save(manager);
        return tokenFor(manager);
    }
}
