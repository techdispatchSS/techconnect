package silver.solutions.techconnect.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByTechnicianIdOrderByCreatedAtDesc(UUID technicianId);

    List<Job> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    Optional<Job> findByDispatchId(UUID dispatchId);

    List<Job> findByIncidentIdIn(Collection<UUID> incidentIds);

    List<Job> findByDispatchIdIn(Collection<UUID> dispatchIds);

    List<Job> findByTechnicianIdInAndStatusIn(Collection<UUID> technicianIds, Collection<JobStatus> statuses);

    List<Job> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    long countByStatusIn(Collection<JobStatus> statuses);

    boolean existsByTechnicianIdAndStatusIn(UUID technicianId, Collection<JobStatus> statuses);

    /** Row-locked, so a status change and the controller closing the job cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Jobs in the given stages, matched against the client, ticket, site and technician. Closed
     * jobs come newest-closed first; everything else most urgent first (the SLA that runs out
     * soonest), so what needs a person's attention is at the top. {@code q}
     * is a ready-made lower-case {@code like} pattern, or empty for no search; it is always bound
     * (never null) so Postgres can type the parameter.
     */
    @Query(value = """
            select j from Job j, Incident i, User u
            where i.id = j.incidentId and u.id = j.technicianId
              and j.status in :statuses
              and (:q = '' or lower(i.clientName) like :q
                   or lower(u.name) like :q
                   or lower(coalesce(i.freshdeskTicketId, '')) like :q
                   or lower(coalesce(i.siteAddress, '')) like :q)
            order by
              case when j.status = silver.solutions.techconnect.entity.JobStatus.CLOSED
                   then j.closedAt end desc nulls last,
              i.slaDueAt asc nulls last,
              j.createdAt desc
            """,
            countQuery = """
            select count(j) from Job j, Incident i, User u
            where i.id = j.incidentId and u.id = j.technicianId
              and j.status in :statuses
              and (:q = '' or lower(i.clientName) like :q
                   or lower(u.name) like :q
                   or lower(coalesce(i.freshdeskTicketId, '')) like :q
                   or lower(coalesce(i.siteAddress, '')) like :q)
            """)
    Page<Job> search(
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("q") String q,
            Pageable pageable);
}
