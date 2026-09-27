package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.Job;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByTechnicianIdOrderByCreatedAtDesc(UUID technicianId);

    List<Job> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}
