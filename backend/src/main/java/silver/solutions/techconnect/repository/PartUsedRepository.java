package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.PartUsed;

public interface PartUsedRepository extends JpaRepository<PartUsed, UUID> {

    List<PartUsed> findByJobId(UUID jobId);
}
