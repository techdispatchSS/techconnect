package silver.solutions.techconnect.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;

public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {

    List<Dispatch> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<Dispatch> findByIncidentIdIn(Collection<UUID> incidentIds);

    List<Dispatch> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /**
     * True while the incident has a dispatch that has been taken, or one still inside its
     * response window. A PENDING row whose window has passed does not count even if the expiry
     * sweep has not reached it yet — it can no longer be accepted, so it must not block a
     * re-dispatch.
     */
    @Query("""
            select count(d) > 0 from Dispatch d
            where d.incidentId = :incidentId
              and (d.status = :accepted or (d.status = :pending and d.expiresAt > :now))
            """)
    boolean hasLiveDispatch(
            @Param("incidentId") UUID incidentId,
            @Param("accepted") DispatchStatus accepted,
            @Param("pending") DispatchStatus pending,
            @Param("now") Instant now);

    /**
     * The dispatches still waiting on this technician's answer: open, inside the window, and not
     * yet answered by them. Soonest to lapse first.
     */
    @Query("""
            select d from Dispatch d, DispatchResponse r
            where r.dispatchId = d.id and r.technicianId = :technicianId
              and r.response = :unanswered
              and d.status = :pending and d.expiresAt > :now
            order by d.expiresAt asc
            """)
    List<Dispatch> findAwaitingAnswerFrom(
            @Param("technicianId") UUID technicianId,
            @Param("unanswered") DispatchResponseStatus unanswered,
            @Param("pending") DispatchStatus pending,
            @Param("now") Instant now);

    /** Ids only: the sweep re-reads and locks each one in its own transaction. */
    @Query("select d.id from Dispatch d where d.status = :status and d.expiresAt <= :now")
    List<UUID> findIdsByStatusAndExpiresAtBefore(
            @Param("status") DispatchStatus status, @Param("now") Instant now);

    /**
     * Row-locked read: a technician's accept, another's accept, and the expiry sweep all decide
     * the same dispatch, and exactly one of them may win.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispatch d where d.id = :id")
    Optional<Dispatch> findByIdForUpdate(@Param("id") UUID id);
}
