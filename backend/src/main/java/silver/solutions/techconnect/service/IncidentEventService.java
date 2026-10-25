package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.controller.IncidentEventResponse;
import silver.solutions.techconnect.entity.IncidentEvent;
import silver.solutions.techconnect.entity.IncidentEventType;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.repository.IncidentEventRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * A ticket's history (FR-09): who did what to it, and when. Events are written in the same
 * transaction as the action they describe, so an action that rolls back leaves no trace and one
 * that commits can never be missing from the history.
 */
@Service
public class IncidentEventService {

    private static final int MAX_SUMMARY = 500;

    private final IncidentEventRepository events;
    private final IncidentRepository incidents;
    private final UserRepository users;

    public IncidentEventService(
            IncidentEventRepository events, IncidentRepository incidents, UserRepository users) {
        this.events = events;
        this.incidents = incidents;
        this.users = users;
    }

    /** Joins the caller's transaction; there is no meaningful "record it on its own". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            UUID incidentId, IncidentEventType type, UUID actorId, UUID dispatchId, String summary) {
        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incidentId);
        event.setEventType(type);
        event.setActorUserId(actorId);
        event.setDispatchId(dispatchId);
        event.setSummary(summary.length() > MAX_SUMMARY ? summary.substring(0, MAX_SUMMARY) : summary);
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    @Transactional(readOnly = true)
    public List<IncidentEventResponse> timeline(UUID incidentId) {
        if (!incidents.existsById(incidentId)) {
            throw ApiException.notFound("Incident not found");
        }
        List<IncidentEvent> history = events.findByIncidentIdOrderByCreatedAtAsc(incidentId);

        Set<UUID> actorIds = history.stream()
                .map(IncidentEvent::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = actorIds.isEmpty()
                ? Map.of()
                : users.findAllById(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName));

        return history.stream()
                .map(e -> new IncidentEventResponse(
                        e.getId(),
                        e.getEventType(),
                        e.getActorUserId() == null ? null : names.get(e.getActorUserId()),
                        e.getSummary(),
                        e.getCreatedAt()))
                .toList();
    }
}
