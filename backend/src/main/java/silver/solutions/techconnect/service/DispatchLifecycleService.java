package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import silver.solutions.techconnect.dto.response.controller.DispatchAnswerResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.DispatchResponseRepository;
import silver.solutions.techconnect.repository.IncidentRepository;

/**
 * What happens to a dispatch after it has gone out (PRD FR-04): a technician accepts or
 * declines, the first accept wins, and a dispatch nobody takes lapses and puts the incident
 * back in front of the Controller.
 *
 * <p>Every decision is made under a row lock on the dispatch, so two technicians accepting at
 * the same instant — or an accept racing the expiry sweep — resolve to exactly one winner.
 */
@Service
public class DispatchLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DispatchLifecycleService.class);

    /** What a technician's accept or decline amounted to. */
    public enum Outcome {
        ACCEPTED,
        DECLINED,
        /** This technician had already answered this dispatch. */
        ALREADY_RESPONDED,
        /** Someone else accepted first. */
        TAKEN,
        /** The response window passed, or every invitee declined. */
        EXPIRED
    }

    private final DispatchRepository dispatches;
    private final DispatchResponseRepository responses;
    private final IncidentRepository incidents;
    private final JobService jobs;
    private final TransactionTemplate transactions;

    public DispatchLifecycleService(
            DispatchRepository dispatches,
            DispatchResponseRepository responses,
            IncidentRepository incidents,
            JobService jobs,
            PlatformTransactionManager transactionManager) {
        this.dispatches = dispatches;
        this.responses = responses;
        this.incidents = incidents;
        this.jobs = jobs;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * The API's answer for an {@link Outcome}: success for an accept or decline, and a 409 with
     * a plain explanation for the cases where the technician's answer did not take effect.
     * Static so callers never reach it through the transactional proxy by mistake.
     */
    public static DispatchAnswerResponse toResponse(UUID dispatchId, Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED, DECLINED -> new DispatchAnswerResponse(dispatchId, outcome.name());
            case TAKEN -> throw ApiException.conflict(
                    "Another technician has already accepted this job.");
            case EXPIRED -> throw ApiException.conflict(
                    "This dispatch has expired and can no longer be accepted.");
            case ALREADY_RESPONDED -> throw ApiException.conflict(
                    "You have already responded to this dispatch.");
        };
    }

    /**
     * Records one technician's answer. A technician who was not invited gets the same "not
     * found" as one asking about a dispatch that does not exist, so ids cannot be probed.
     */
    @Transactional
    public Outcome respond(UUID technicianId, UUID dispatchId, boolean accept) {
        Dispatch dispatch = dispatches.findByIdForUpdate(dispatchId)
                .orElseThrow(() -> ApiException.notFound("Dispatch not found"));
        DispatchResponse mine = responses.findByDispatchIdAndTechnicianId(dispatchId, technicianId)
                .orElseThrow(() -> ApiException.notFound("Dispatch not found"));

        if (dispatch.getStatus() == DispatchStatus.ACCEPTED) {
            // Repeating the winning accept (a retried request) is not an error.
            return mine.getResponse() == DispatchResponseStatus.ACCEPTED && accept
                    ? Outcome.ACCEPTED
                    : Outcome.TAKEN;
        }
        if (dispatch.getStatus() == DispatchStatus.EXPIRED) {
            return Outcome.EXPIRED;
        }
        if (!dispatch.getExpiresAt().isAfter(Instant.now())) {
            expire(dispatch);
            return Outcome.EXPIRED;
        }
        if (mine.getResponse() != DispatchResponseStatus.PENDING) {
            return Outcome.ALREADY_RESPONDED;
        }

        Instant now = Instant.now();
        mine.setRespondedAt(now);

        if (accept) {
            mine.setResponse(DispatchResponseStatus.ACCEPTED);
            responses.save(mine);
            dispatch.setStatus(DispatchStatus.ACCEPTED);
            dispatches.save(dispatch);
            // The work now has an owner and a job to follow, in the same transaction as the accept.
            jobs.openFor(dispatch, technicianId);
            // The job is gone for everyone else; they will not be answering it.
            lapsePending(dispatchId);
            log.info("Dispatch {} accepted by technician {}", dispatchId, technicianId);
            return Outcome.ACCEPTED;
        }

        mine.setResponse(DispatchResponseStatus.DECLINED);
        responses.save(mine);
        boolean anyoneLeft = responses.findByDispatchId(dispatchId).stream()
                .anyMatch(r -> r.getResponse() == DispatchResponseStatus.PENDING);
        if (!anyoneLeft) {
            // Nobody who was asked can still take it — no point holding the window open.
            expire(dispatch);
        }
        log.info("Dispatch {} declined by technician {}", dispatchId, technicianId);
        return Outcome.DECLINED;
    }

    /**
     * Lapses every dispatch whose response window has passed without an acceptance and returns
     * how many it expired. Each is handled in its own transaction so one bad row cannot hold
     * back the rest.
     */
    @Scheduled(fixedDelayString = "${techconnect.dispatch.expiry-sweep-interval:PT15S}")
    public int expireDue() {
        Instant now = Instant.now();
        List<UUID> due = dispatches.findIdsByStatusAndExpiresAtBefore(DispatchStatus.PENDING, now);
        int expired = 0;
        for (UUID id : due) {
            try {
                Boolean done = transactions.execute(status -> dispatches.findByIdForUpdate(id)
                        .filter(d -> d.getStatus() == DispatchStatus.PENDING
                                && !d.getExpiresAt().isAfter(Instant.now()))
                        .map(d -> {
                            expire(d);
                            return true;
                        })
                        .orElse(false));
                if (Boolean.TRUE.equals(done)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("Could not expire dispatch {}", id, e);
            }
        }
        return expired;
    }

    /** Marks the dispatch lapsed, closes out whoever never answered, and re-queues the incident. */
    private void expire(Dispatch dispatch) {
        dispatch.setStatus(DispatchStatus.EXPIRED);
        dispatches.save(dispatch);
        lapsePending(dispatch.getId());
        requeueIfUnstaffed(dispatch.getIncidentId());
        log.info("Dispatch {} expired for incident {}", dispatch.getId(), dispatch.getIncidentId());
    }

    private void lapsePending(UUID dispatchId) {
        for (DispatchResponse response : responses.findByDispatchId(dispatchId)) {
            if (response.getResponse() == DispatchResponseStatus.PENDING) {
                response.setResponse(DispatchResponseStatus.NO_RESPONSE);
                responses.save(response);
            }
        }
    }

    /**
     * An incident whose only dispatch lapsed goes back to NEW, so it reappears in the queue (and
     * raises the new-incident alert) instead of sitting "In progress" with nobody on it.
     */
    private void requeueIfUnstaffed(UUID incidentId) {
        Incident incident = incidents.findByIdForUpdate(incidentId).orElse(null);
        if (incident == null || incident.getStatus() != IncidentStatus.IN_PROGRESS) {
            return;
        }
        boolean stillStaffed = dispatches.hasLiveDispatch(
                incidentId, DispatchStatus.ACCEPTED, DispatchStatus.PENDING, Instant.now());
        if (!stillStaffed) {
            incident.setStatus(IncidentStatus.NEW);
            incidents.save(incident);
        }
    }
}
