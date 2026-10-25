package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.FreshdeskUpdate;

public interface FreshdeskUpdateRepository extends JpaRepository<FreshdeskUpdate, Long> {

    List<FreshdeskUpdate> findByIncidentIdOrderByIdAsc(UUID incidentId);

    List<FreshdeskUpdate> findByDeliveryOrderByIdAsc(FreshdeskUpdate.Delivery delivery, Limit limit);
}
