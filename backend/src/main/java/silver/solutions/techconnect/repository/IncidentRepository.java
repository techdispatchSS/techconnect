package silver.solutions.techconnect.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentStatus;

public interface IncidentRepository
        extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    long countByStatus(IncidentStatus status);

    Optional<Incident> findByFreshdeskTicketId(String freshdeskTicketId);

    /**
     * Row-locked read, so two Controllers dispatching the same incident at once (or a dispatch
     * racing the sweep that re-queues an expired one) serialise instead of both passing the
     * "no live dispatch yet" check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> findByIdForUpdate(@Param("id") UUID id);

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

    /**
     * Past its SLA and still open. Computed from the clock rather than read from the
     * {@code OVERDUE} status, which nothing would ever write: an incident is overdue the moment
     * its SLA passes, whether it is new or already being worked. A row someone has explicitly
     * marked {@code OVERDUE} still counts.
     */
    @Query("""
            select count(i) from Incident i
            where i.status <> :closed
              and (i.status = :overdue or (i.slaDueAt is not null and i.slaDueAt < :now))
            """)
    long countOverdue(
            @Param("closed") IncidentStatus closed,
            @Param("overdue") IncidentStatus overdue,
            @Param("now") Instant now);
}
