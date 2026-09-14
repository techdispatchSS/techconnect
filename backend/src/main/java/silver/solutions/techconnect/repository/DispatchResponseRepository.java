package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.DispatchResponse;

public interface DispatchResponseRepository extends JpaRepository<DispatchResponse, UUID> {

    List<DispatchResponse> findByDispatchId(UUID dispatchId);
}
