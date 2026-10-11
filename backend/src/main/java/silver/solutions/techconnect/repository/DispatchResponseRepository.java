package silver.solutions.techconnect.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.DispatchResponse;

public interface DispatchResponseRepository extends JpaRepository<DispatchResponse, UUID> {

    List<DispatchResponse> findByDispatchId(UUID dispatchId);

    List<DispatchResponse> findByDispatchIdIn(Collection<UUID> dispatchIds);

    Optional<DispatchResponse> findByDispatchIdAndTechnicianId(UUID dispatchId, UUID technicianId);
}
