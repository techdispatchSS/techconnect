package silver.solutions.techdispatch.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techdispatch.domain.TechnicianProfile;

public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, UUID> {
}
