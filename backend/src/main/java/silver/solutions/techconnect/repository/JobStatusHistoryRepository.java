package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.JobStatusHistory;

public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, UUID> {

    List<JobStatusHistory> findByJobIdOrderByChangedAtDesc(UUID jobId);
}
