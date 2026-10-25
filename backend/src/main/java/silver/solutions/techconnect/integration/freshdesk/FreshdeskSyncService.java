package silver.solutions.techconnect.integration.freshdesk;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentEventType;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.service.IncidentEventService;

/**
 * Pulls tickets from Freshdesk and turns them into incidents (PRD FR-02). Safe to run as often
 * as you like: a ticket is matched to its incident by Freshdesk ticket id, so a repeat pass
 * changes nothing, and two passes racing each other (a poll and a manual sync, or two backend
 * instances) end with one incident, not two.
 */
@Service
public class FreshdeskSyncService {

    private static final Logger log = LoggerFactory.getLogger(FreshdeskSyncService.class);

    /** What one pass did. */
    public record Result(int created, int updated, int closed, int skipped) {
        static final Result NONE = new Result(0, 0, 0, 0);
    }

    /** How the connection stands, for the dashboard header. */
    public record Status(
            boolean connected, Instant lastSyncAt, Instant lastSuccessAt, String lastError) {}

    private enum Change { CREATED, UPDATED, CLOSED, UNCHANGED, SKIPPED }

    private final FreshdeskClient client;
    private final IncidentRepository incidents;
    private final IncidentEventService events;
    private final TransactionTemplate transactions;
    private final ReentrantLock running = new ReentrantLock();

    private volatile Instant cursor = Instant.EPOCH;
    private volatile Instant lastSyncAt;
    private volatile Instant lastSuccessAt;
    private volatile String lastError;

    public FreshdeskSyncService(
            ObjectProvider<FreshdeskClient> client,
            IncidentRepository incidents,
            IncidentEventService events,
            PlatformTransactionManager transactionManager) {
        // Absent until a Freshdesk (simulated or live) is configured; the sync is then a no-op.
        this.client = client.getIfAvailable();
        this.incidents = incidents;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public boolean connected() {
        return client != null;
    }

    public Status status() {
        return new Status(connected(), lastSyncAt, lastSuccessAt, lastError);
    }

    @Scheduled(
            fixedDelayString = "${techconnect.freshdesk.poll-interval:PT30S}",
            initialDelayString = "PT10S")
    public void poll() {
        sync();
    }

    /** One pass over everything Freshdesk has changed since the last successful one. */
    public Result sync() {
        if (client == null) {
            return Result.NONE;
        }
        if (!running.tryLock()) {
            // A pass is already in flight; it will pick up whatever this one would have.
            return Result.NONE;
        }
        try {
            lastSyncAt = Instant.now();
            List<FreshdeskTicket> tickets = client.ticketsUpdatedSince(cursor);

            int created = 0;
            int updated = 0;
            int closed = 0;
            int skipped = 0;
            Instant newest = null;
            boolean clean = true;

            for (FreshdeskTicket ticket : tickets) {
                try {
                    switch (transactions.execute(status -> upsert(ticket))) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case CLOSED -> closed++;
                        case SKIPPED -> skipped++;
                        case UNCHANGED -> { }
                    }
                    if (ticket.updatedAt() != null
                            && (newest == null || ticket.updatedAt().isAfter(newest))) {
                        newest = ticket.updatedAt();
                    }
                } catch (DataIntegrityViolationException e) {
                    // Another pass (or instance) imported the same ticket between our read and
                    // write. It is there now; the next pass will see it as existing.
                    log.debug("Freshdesk ticket {} was imported concurrently", ticket.id());
                } catch (RuntimeException e) {
                    clean = false;
                    log.error("Could not import Freshdesk ticket {}", ticket.id(), e);
                }
            }

            // Only move the cursor past tickets that all landed, so a failed one is retried. It
            // moves just beyond the newest one, so the next pass sees only what changed since.
            if (clean) {
                if (newest != null) {
                    cursor = newest.plusMillis(1);
                }
                lastError = null;
            } else {
                lastError = "Some tickets could not be imported; will retry.";
            }
            lastSuccessAt = Instant.now();
            if (created + updated + closed > 0) {
                log.info("Freshdesk sync: {} created, {} updated, {} closed", created, updated, closed);
            }
            return new Result(created, updated, closed, skipped);
        } catch (RuntimeException e) {
            lastError = "Freshdesk could not be reached.";
            log.error("Freshdesk sync failed", e);
            return Result.NONE;
        } finally {
            running.unlock();
        }
    }

    private Change upsert(FreshdeskTicket ticket) {
        Incident existing = incidents.findByFreshdeskTicketId(ticket.id()).orElse(null);

        if (existing == null) {
            if (ticket.isResolved()) {
                // Already finished before we ever saw it: nothing for a Controller to do.
                return Change.SKIPPED;
            }
            Incident incident = new Incident();
            FreshdeskIncidentMapper.apply(ticket, incident);
            incident.setStatus(IncidentStatus.NEW);
            if (ticket.createdAt() != null) {
                // The age on the queue is time since the client raised it, not since we polled.
                incident.setCreatedAt(ticket.createdAt());
            }
            incidents.saveAndFlush(incident);
            events.record(incident.getId(), IncidentEventType.INCIDENT_RECEIVED, null, null,
                    "Received from Freshdesk (" + ticket.id() + ")");
            return Change.CREATED;
        }

        if (ticket.isResolved()) {
            if (existing.getStatus() == IncidentStatus.CLOSED) {
                return Change.UNCHANGED;
            }
            existing.setStatus(IncidentStatus.CLOSED);
            existing.setClosedAt(java.time.Instant.now());
            incidents.save(existing);
            events.record(existing.getId(), IncidentEventType.INCIDENT_CLOSED, null, null,
                    "Closed: the Freshdesk ticket (" + ticket.id() + ") was resolved");
            return Change.CLOSED;
        }

        if (existing.getStatus() == IncidentStatus.CLOSED) {
            // Closed here (or earlier from Freshdesk) and the ticket is merely being edited.
            // Reopening is a decision for a person, not something a sync should do.
            return Change.UNCHANGED;
        }

        FreshdeskIncidentMapper.apply(ticket, existing);
        incidents.save(existing);
        return Change.UPDATED;
    }
}
