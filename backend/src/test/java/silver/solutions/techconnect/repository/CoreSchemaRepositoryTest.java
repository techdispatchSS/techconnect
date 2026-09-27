package silver.solutions.techconnect.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.DispatchType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.JobStatusHistory;
import silver.solutions.techconnect.entity.PartUsed;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;

/**
 * #9's "basic repository tests ... proving mappings load against the Flyway schema" — one save/
 * reload round trip through every aggregate root the issue lists that main didn't already have
 * coverage for: {@link Incident}, {@link Dispatch}, {@link DispatchResponse}, {@link Job},
 * {@link PartUsed} and {@link JobStatusHistory}. {@link User} and {@code TechnicianProfile} are
 * exercised elsewhere already (AdminPortalIntegrationTest).
 *
 * <p>{@code replace = Replace.NONE} keeps the real Postgres container instead of swapping in an
 * embedded database — this schema uses Postgres-specific features (jsonb, partial unique
 * indexes) an in-memory database can't emulate, the same reasoning as
 * {@code AbstractPostgresIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CoreSchemaRepositoryTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("techconnect")
                    .withUsername("techconnect")
                    .withPassword("techconnect");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JobRepository jobRepository;
    @Autowired private PartUsedRepository partUsedRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private DispatchRepository dispatchRepository;
    @Autowired private DispatchResponseRepository dispatchResponseRepository;

    @Test
    void savesAndLoadsTheFullIncidentToJobChainThroughEachRepository() {
        User technician = new User();
        technician.setEmail("job-repo-test@techconnect.test");
        technician.setName("Job Repo Technician");
        technician.setRole(UserRole.TECHNICIAN);
        technician.setStatus(UserStatus.ACTIVE);
        technician = userRepository.save(technician);

        Incident incident = new Incident();
        incident.setClientName("Job Repo Test Client");
        incident = incidentRepository.save(incident);

        Dispatch dispatch = new Dispatch();
        dispatch.setIncidentId(incident.getId());
        dispatch.setDispatchType(DispatchType.ASSIGN);
        dispatch.setStatus(DispatchStatus.ACCEPTED);
        dispatch.setExpiresAt(Instant.now().plusSeconds(600));
        dispatch = dispatchRepository.save(dispatch);

        DispatchResponse response = new DispatchResponse();
        response.setDispatchId(dispatch.getId());
        response.setTechnicianId(technician.getId());
        response.setResponse(DispatchResponseStatus.ACCEPTED);
        response.setRespondedAt(Instant.now());
        dispatchResponseRepository.save(response);

        Job job = new Job();
        job.setIncidentId(incident.getId());
        job.setDispatchId(dispatch.getId());
        job.setTechnicianId(technician.getId());
        job.setStatus(JobStatus.WORK_STARTED);
        job.setStartedAt(Instant.now().minusSeconds(1200));
        job.setArrivedAt(Instant.now().minusSeconds(900));
        job.setLabourHours(new BigDecimal("1.50"));
        job = jobRepository.save(job);

        PartUsed part = new PartUsed();
        part.setJobId(job.getId());
        part.setPartName("Replacement router");
        part.setQuantity(2);
        part.setSerialNumber("SN-12345");
        partUsedRepository.save(part);

        JobStatusHistory history = new JobStatusHistory();
        history.setJobId(job.getId());
        history.setStatus(JobStatus.WORK_STARTED);
        history.setPreviousStatus(JobStatus.ON_SITE);
        history.setReason("Technician began repairs");
        history.setChangedBy(technician.getId());
        jobStatusHistoryRepository.save(history);

        UUID technicianId = technician.getId();

        Incident reloadedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloadedIncident.getClientName()).isEqualTo("Job Repo Test Client");
        assertThat(reloadedIncident.getStatus()).isEqualTo(IncidentStatus.NEW);

        Dispatch reloadedDispatch = dispatchRepository.findById(dispatch.getId()).orElseThrow();
        assertThat(reloadedDispatch.getDispatchType()).isEqualTo(DispatchType.ASSIGN);
        assertThat(reloadedDispatch.getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(reloadedDispatch.getIncidentId()).isEqualTo(incident.getId());

        assertThat(dispatchResponseRepository.findByDispatchId(dispatch.getId()))
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.getTechnicianId()).isEqualTo(technicianId);
                            assertThat(r.getResponse()).isEqualTo(DispatchResponseStatus.ACCEPTED);
                        });

        Job reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.WORK_STARTED);
        assertThat(reloaded.getTechnicianId()).isEqualTo(technicianId);
        assertThat(reloaded.getDispatchId()).isEqualTo(dispatch.getId());
        assertThat(reloaded.getLabourHours()).isEqualByComparingTo("1.50");

        assertThat(jobRepository.findByTechnicianIdOrderByCreatedAtDesc(technicianId))
                .extracting(Job::getId)
                .containsExactly(job.getId());
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()))
                .extracting(Job::getId)
                .containsExactly(job.getId());

        assertThat(partUsedRepository.findByJobId(job.getId()))
                .singleElement()
                .satisfies(
                        p -> {
                            assertThat(p.getPartName()).isEqualTo("Replacement router");
                            assertThat(p.getQuantity()).isEqualTo(2);
                            assertThat(p.getSerialNumber()).isEqualTo("SN-12345");
                        });

        assertThat(jobStatusHistoryRepository.findByJobIdOrderByChangedAtDesc(job.getId()))
                .singleElement()
                .satisfies(
                        h -> {
                            assertThat(h.getStatus()).isEqualTo(JobStatus.WORK_STARTED);
                            assertThat(h.getPreviousStatus()).isEqualTo(JobStatus.ON_SITE);
                            assertThat(h.getChangedBy()).isEqualTo(technicianId);
                        });
    }
}
