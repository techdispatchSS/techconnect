package silver.solutions.techconnect.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
