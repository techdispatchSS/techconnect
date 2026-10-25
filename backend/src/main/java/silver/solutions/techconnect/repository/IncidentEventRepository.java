package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import silver.solutions.techconnect.entity.IncidentEvent;

/**
 * The bare {@link Repository} marker on purpose, for the same reason as
 * {@link AdminAuditLogRepository}: a ticket's history must not be editable or deletable, so no
 * caller is handed {@code delete} or bulk operations.
 */
public interface IncidentEventRepository extends Repository<IncidentEvent, UUID> {

    IncidentEvent save(IncidentEvent event);

    List<IncidentEvent> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
