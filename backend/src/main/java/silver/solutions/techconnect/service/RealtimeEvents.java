package silver.solutions.techconnect.service;

import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import silver.solutions.techconnect.dto.response.common.RealtimeEvent;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.repository.DispatchResponseRepository;

/**
 * Tells the open screens that a dispatch or job changed (FR-05, §9.1: on the dashboard within 3
 * seconds). Sent only once the change is committed, or a screen could re-read and find the old
 * state. A failed push never affects the change itself: the screens' polling picks it up anyway.
 */
@Service
public class RealtimeEvents {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEvents.class);

    /** Every controller watches every dispatch and job. */
    public static final String CONTROLLER_TOPIC = "/topic/controller/events";
    /** A technician's own events, addressed through {@code /user} to their sessions alone. */
    public static final String USER_QUEUE = "/queue/events";

    private final SimpMessagingTemplate messaging;
    private final DispatchResponseRepository responses;

    public RealtimeEvents(SimpMessagingTemplate messaging, DispatchResponseRepository responses) {
        this.messaging = messaging;
        this.responses = responses;
    }

    /** A dispatch was sent, answered or lapsed: its controllers and everyone it was offered to. */
    public void dispatchChanged(Dispatch dispatch, Collection<DispatchResponse> invited) {
        List<String> technicians = invited.stream()
                .map(r -> r.getTechnicianId().toString())
                .toList();
        send(new RealtimeEvent(
                RealtimeEvent.Type.DISPATCH_CHANGED,
                dispatch.getIncidentId(),
                dispatch.getId(),
                null,
                dispatch.getStatus().name()), technicians);
    }

    public void dispatchChanged(Dispatch dispatch) {
        dispatchChanged(dispatch, responses.findByDispatchId(dispatch.getId()));
    }

    /** A job moved on: the controllers, and the technician who holds it. */
    public void jobChanged(Job job) {
        send(new RealtimeEvent(
                RealtimeEvent.Type.JOB_CHANGED,
                job.getIncidentId(),
                job.getDispatchId(),
                job.getId(),
                job.getStatus().name()), List.of(job.getTechnicianId().toString()));
    }

    private void send(RealtimeEvent event, List<String> technicians) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            push(event, technicians);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                push(event, technicians);
            }
        });
    }

    private void push(RealtimeEvent event, List<String> technicians) {
        try {
            messaging.convertAndSend(CONTROLLER_TOPIC, event);
            technicians.forEach(id -> messaging.convertAndSendToUser(id, USER_QUEUE, event));
        } catch (RuntimeException e) {
            log.warn("Could not push {} for incident {}", event.type(), event.incidentId(), e);
        }
    }
}
