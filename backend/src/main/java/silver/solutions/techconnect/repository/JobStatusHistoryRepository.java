package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import silver.solutions.techconnect.entity.JobStatusHistory;

/** Insert and read only (FR-09): deliberately not a JpaRepository, which brings delete methods. */
public interface JobStatusHistoryRepository extends Repository<JobStatusHistory, UUID> {

    JobStatusHistory save(JobStatusHistory entry);

    List<JobStatusHistory> findByJobIdOrderByChangedAtDesc(UUID jobId);
}
