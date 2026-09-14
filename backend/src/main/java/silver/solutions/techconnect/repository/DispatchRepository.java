package silver.solutions.techconnect.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.Dispatch;

public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {

    List<Dispatch> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<Dispatch> findByIncidentIdIn(Collection<UUID> incidentIds);
}
