package silver.solutions.techconnect.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.ApplicationDocument;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

    List<ApplicationDocument> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    List<ApplicationDocument> findByApplicationIdIn(Collection<UUID> applicationIds);
}
