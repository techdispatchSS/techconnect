package silver.solutions.techconnect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentStatus;

public interface IncidentRepository
        extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    long countByStatus(IncidentStatus status);

    /** Not CLOSED, and with no dispatch that hasn't lapsed to EXPIRED — i.e. never staffed. */
    @Query("""
            select count(i) from Incident i
            where i.status <> :closed
              and not exists (
                select 1 from Dispatch d
                where d.incidentId = i.id and d.status <> :expired
              )
            """)
    long countUnassigned(@Param("closed") IncidentStatus closed, @Param("expired") DispatchStatus expired);
}
