package silver.solutions.techconnect.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.controller.IncidentLock;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * A controller who opens an incident to dispatch it holds it for a short while, so another
 * controller does not start the same dispatch and send the same fault to a different technician.
 *
 * <p>The hold lasts {@link #HOLD} and is renewed while the dispatch screen is open; if the tab
 * closes or the connection drops it simply lapses. Taking it is a row-locked decision on the
 * incident, so two controllers opening it at the same instant resolve to one holder. Creating a
 * dispatch checks it too ({@link DispatchService#create}), and independently refuses a second
 * live dispatch, so the lock is a courtesy to the people and not what keeps the data right.
 */
@Service
public class IncidentLockService {

    private static final Logger log = LoggerFactory.getLogger(IncidentLockService.class);

    /** How long a hold lasts without being renewed. The screen renews it well inside this. */
    public static final Duration HOLD = Duration.ofMinutes(2);

    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final UserRepository users;

    public IncidentLockService(
            IncidentRepository incidents, DispatchRepository dispatches, UserRepository users) {
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.users = users;
    }

    /**
     * Takes the hold, or renews it if this controller already has it. A 409 names whoever else has
     * it, and a closed or already-dispatched incident cannot be held at all: there is nothing to
     * dispatch.
     */
    @Transactional
    public IncidentLock acquire(UUID incidentId, UUID controllerId) {
        Incident incident = incidents.findByIdForUpdate(incidentId)
                .orElseThrow(() -> ApiException.notFound("Incident not found"));
        Instant now = Instant.now();

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw ApiException.conflict("This incident is closed and can't be dispatched.");
        }
        UUID holder = incident.liveLockHolder(now);
        if (holder != null && !holder.equals(controllerId)) {
            throw ApiException.conflict(heldBy(holder));
        }
        if (dispatches.hasLiveDispatch(incidentId, DispatchStatus.ACCEPTED, DispatchStatus.PENDING, now)) {
            throw ApiException.conflict(
                    "This incident already has an active dispatch. Wait for it to be answered or expire.");
        }

        incident.setLockedBy(controllerId);
        incident.setLockExpiresAt(now.plus(HOLD));
        incidents.save(incident);
        if (holder == null) {
            log.info("Incident {} taken for dispatch by {}", incidentId, controllerId);
        }
        return new IncidentLock(controllerId, nameOf(controllerId), incident.getLockExpiresAt());
    }

    /** Lets go of the hold if this controller has it. Anyone else's hold is left alone. */
    @Transactional
    public void release(UUID incidentId, UUID controllerId) {
        incidents.findByIdForUpdate(incidentId).ifPresent(incident -> {
            if (controllerId.equals(incident.getLockedBy())) {
                clear(incident);
                incidents.save(incident);
            }
        });
    }

    /**
     * For {@link DispatchService#create}, with the incident row already locked: refuses while
     * someone else holds it. The holder (or nobody) passes.
     */
    void requireNotHeldByAnother(Incident incident, UUID controllerId) {
        UUID holder = incident.liveLockHolder(Instant.now());
        if (holder != null && !holder.equals(controllerId)) {
            throw ApiException.conflict(heldBy(holder));
        }
    }

    /** A dispatch going out ends the hold: the incident is no longer waiting to be dispatched. */
    void clear(Incident incident) {
        incident.setLockedBy(null);
        incident.setLockExpiresAt(null);
    }

    private String heldBy(UUID holder) {
        return nameOf(holder) + " is dispatching this incident right now. Try again in a moment, "
                + "or ask them to finish.";
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(u -> u.getName()).orElse("Another controller");
    }
}
