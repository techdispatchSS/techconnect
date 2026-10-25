package silver.solutions.techconnect.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import silver.solutions.techconnect.dto.response.controller.ResendDispatchResponse;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskUpdateService;
import silver.solutions.techconnect.mapper.IncidentMapper;
import silver.solutions.techconnect.dto.request.controller.CreateDispatchRequest;
import silver.solutions.techconnect.dto.response.controller.DispatchCreateResponse;
import silver.solutions.techconnect.dto.response.controller.TechnicianCandidateResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.DispatchType;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentEventType;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianSkill;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.DispatchMapper;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.DispatchResponseRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.TechnicianSkillRepository;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * Technician ranking and dispatch creation (PRD FR-04). Access is restricted to the
 * CONTROLLER role by the {@code /v1/controller/**} rule in {@code SecurityConfig}.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    /** FR-04: a broadcast's response window before it lapses and re-queues. */
    private static final Duration BROADCAST_WINDOW = Duration.ofMinutes(10);

    /** Least time between two notifications of one dispatch, so a button can't be used to spam. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final DispatchResponseRepository dispatchResponses;
    private final UserRepository users;
    private final TechnicianProfileRepository technicianProfiles;
    private final TechnicianSkillRepository technicianSkills;
    private final DispatchMapper dispatchMapper;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notifications;
    private final IncidentLockService locks;
    private final FreshdeskUpdateService freshdesk;
    private final IncidentEventService events;

    public DispatchService(
            IncidentRepository incidents,
            DispatchRepository dispatches,
            DispatchResponseRepository dispatchResponses,
            UserRepository users,
            TechnicianProfileRepository technicianProfiles,
            TechnicianSkillRepository technicianSkills,
            DispatchMapper dispatchMapper,
            JdbcTemplate jdbcTemplate,
            NotificationService notifications,
            IncidentLockService locks,
            FreshdeskUpdateService freshdesk,
            IncidentEventService events) {
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.dispatchResponses = dispatchResponses;
        this.users = users;
        this.technicianProfiles = technicianProfiles;
        this.technicianSkills = technicianSkills;
        this.dispatchMapper = dispatchMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.notifications = notifications;
        this.locks = locks;
        this.freshdesk = freshdesk;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TechnicianCandidateResponse> recommend(UUID incidentId, List<String> requiredSkills) {
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        List<User> candidates = users.findByRoleAndStatus(UserRole.TECHNICIAN, UserStatus.ACTIVE);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = candidates.stream().map(User::getId).toList();
        Map<UUID, TechnicianProfile> profiles = technicianProfiles.findAllById(ids).stream()
                .collect(Collectors.toMap(TechnicianProfile::getUserId, p -> p));
        Map<UUID, Set<String>> skillsByUser = technicianSkills.findByUserIdIn(ids).stream()
                .collect(Collectors.groupingBy(TechnicianSkill::getUserId,
                        Collectors.mapping(s -> s.getSkill().toLowerCase(), Collectors.toSet())));
        Map<UUID, Long> jobCounts = completedJobCounts(ids);

        Set<String> required = requiredSkills == null ? Set.of() : requiredSkills.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).collect(Collectors.toSet());

        return candidates.stream()
                .map(technician -> {
                    Set<String> skills = skillsByUser.getOrDefault(technician.getId(), Set.of());
                    int matched = (int) required.stream().filter(skills::contains).count();
                    return dispatchMapper.toCandidateResponse(
                            technician,
                            profiles.get(technician.getId()),
                            incident.getSiteLatitude(),
                            incident.getSiteLongitude(),
                            matched,
                            required.size(),
                            jobCounts.getOrDefault(technician.getId(), 0L));
                })
                .sorted(Comparator.comparingInt(TechnicianCandidateResponse::score).reversed())
                .toList();
    }

    @Transactional
    public DispatchCreateResponse create(UUID actorId, CreateDispatchRequest request) {
        // Row-locked so two Controllers sending the same incident out at once serialise: the
        // second one then sees the first one's dispatch instead of creating a competing one.
        Incident incident = incidents.findByIdForUpdate(request.incidentId())
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        if (request.technicianIds().isEmpty()) {
            throw ApiException.badRequest("Select at least one technician.");
        }
        // Someone else is part-way through dispatching this one: let them finish, so the same
        // fault is not sent to different technicians by two controllers.
        locks.requireNotHeldByAnother(incident, actorId);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw ApiException.conflict("This incident is closed and can't be dispatched.");
        }
        if (dispatches.hasLiveDispatch(
                incident.getId(), DispatchStatus.ACCEPTED, DispatchStatus.PENDING, Instant.now())) {
            throw ApiException.conflict(
                    "This incident already has an active dispatch. Wait for it to be answered or expire.");
        }

        List<User> technicians = users.findAllById(request.technicianIds());
        if (technicians.size() != request.technicianIds().size()) {
            throw ApiException.badRequest("One or more selected technicians could not be found.");
        }
        if (!technicians.stream().allMatch(u -> u.getRole() == UserRole.TECHNICIAN)) {
            throw ApiException.badRequest("Only technicians can be dispatched to a job.");
        }
        if (!technicians.stream().allMatch(u -> u.getStatus() == UserStatus.ACTIVE)) {
            throw ApiException.badRequest(
                    "One or more selected technicians are not active and can't be dispatched.");
        }
        if (request.dispatchType() == DispatchType.ASSIGN && request.technicianIds().size() != 1) {
            throw ApiException.badRequest("Assign a job to exactly one technician.");
        }

        Dispatch dispatch = new Dispatch();
        dispatch.setIncidentId(incident.getId());
        dispatch.setDispatchType(request.dispatchType());
        dispatch.setStatus(DispatchStatus.PENDING);
        dispatch.setExpiresAt(Instant.now().plus(BROADCAST_WINDOW));
        dispatch.setCreatedBy(actorId);
        dispatch.setJobType(request.jobType());
        dispatch.setRequiredSkills(join(request.requiredSkills()));
        dispatch.setRequiredCertifications(join(request.requiredCertifications()));
        dispatch.setSlaResponse(request.slaResponse());
        dispatch.setSiteContact(request.siteContact());
        dispatch.setNotesForTechnician(request.notesForTechnician());
        dispatches.save(dispatch);

        for (UUID technicianId : request.technicianIds()) {
            DispatchResponse response = new DispatchResponse();
            response.setDispatchId(dispatch.getId());
            response.setTechnicianId(technicianId);
            response.setResponse(DispatchResponseStatus.PENDING);
            dispatchResponses.save(response);
        }

        if (incident.getStatus() == IncidentStatus.NEW) {
            incident.setStatus(IncidentStatus.IN_PROGRESS);
        }
        locks.clear(incident);
        incidents.save(incident);
        freshdesk.dispatchSent(incident, dispatch, technicians);

        // The first notification goes out with the dispatch; it is counted so the Controller
        // can see it, and sent only once the dispatch is safely committed.
        dispatch.setNotificationCount(1);
        dispatch.setLastNotifiedAt(Instant.now());
        dispatches.save(dispatch);
        notifyAfterCommit(incident, dispatch, technicians, false);
        events.record(incident.getId(), IncidentEventType.DISPATCH_SENT, actorId, dispatch.getId(),
                (request.dispatchType() == DispatchType.ASSIGN ? "Assigned to " : "Broadcast to ")
                        + names(technicians));

        log.info("Dispatch {} ({}) created for incident {} by {} — {} technician(s) invited",
                dispatch.getId(), dispatch.getDispatchType(), incident.getId(), actorId,
                request.technicianIds().size());

        return dispatchMapper.toCreateResponse(dispatch, request.technicianIds());
    }

    /**
     * Sends the same job notice again to every technician who has not yet answered. Declined and
     * answered technicians are left alone, and so is the response window: this is a nudge, not a
     * new dispatch.
     */
    @Transactional
    public ResendDispatchResponse resend(UUID actorId, UUID dispatchId) {
        Dispatch dispatch = dispatches.findByIdForUpdate(dispatchId)
                .orElseThrow(() -> ApiException.notFound("Dispatch not found"));
        Instant now = Instant.now();

        if (dispatch.getStatus() == DispatchStatus.ACCEPTED) {
            throw ApiException.conflict("A technician has already accepted this job.");
        }
        if (dispatch.getStatus() == DispatchStatus.EXPIRED || !dispatch.getExpiresAt().isAfter(now)) {
            throw ApiException.conflict("This dispatch has expired. Send a new dispatch instead.");
        }
        Instant last = dispatch.getLastNotifiedAt();
        if (last != null && Duration.between(last, now).compareTo(RESEND_COOLDOWN) < 0) {
            long wait = RESEND_COOLDOWN.minus(Duration.between(last, now)).toSeconds() + 1;
            throw ApiException.conflict(
                    "Technicians were notified moments ago. You can resend in " + wait + " seconds.");
        }

        List<UUID> waiting = dispatchResponses.findByDispatchId(dispatchId).stream()
                .filter(r -> r.getResponse() == DispatchResponseStatus.PENDING)
                .map(DispatchResponse::getTechnicianId)
                .toList();
        List<User> recipients = users.findAllById(waiting).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .toList();
        if (recipients.isEmpty()) {
            throw ApiException.conflict("There is no one left to notify: everyone has answered.");
        }

        Incident incident = incidents.findById(dispatch.getIncidentId())
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        dispatch.setNotificationCount(dispatch.getNotificationCount() + 1);
        dispatch.setLastNotifiedAt(now);
        dispatches.save(dispatch);
        notifyAfterCommit(incident, dispatch, recipients, true);
        events.record(incident.getId(), IncidentEventType.DISPATCH_RESENT, actorId, dispatchId,
                "Notified again: " + names(recipients));

        log.info("Dispatch {} resent to {} technician(s) (notification #{})",
                dispatchId, recipients.size(), dispatch.getNotificationCount());
        return new ResendDispatchResponse(
                recipients.size(), dispatch.getNotificationCount(), now);
    }

    /**
     * Notifications are best-effort side effects and slow (SMTP), so they go out after the
     * transaction commits — never while holding the dispatch's row lock, and never for a
     * dispatch that then rolls back.
     */
    private void notifyAfterCommit(
            Incident incident, Dispatch dispatch, List<User> recipients, boolean reminder) {
        NotificationService.DispatchNotice notice = new NotificationService.DispatchNotice(
                IncidentMapper.ticketRef(incident),
                incident.getClientName(),
                incident.getSiteAddress(),
                incident.getIssueType(),
                incident.getPriority(),
                dispatch.getNotesForTechnician(),
                dispatch.getExpiresAt(),
                reminder,
                dispatch.getId());

        Runnable send = () -> {
            for (User recipient : recipients) {
                try {
                    notifications.sendDispatchNotice(recipient, notice);
                } catch (RuntimeException e) {
                    log.warn("Could not notify technician {} of dispatch {}",
                            recipient.getId(), dispatch.getId(), e);
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private static String names(List<User> technicians) {
        return technicians.stream().map(User::getName).sorted().collect(Collectors.joining(", "));
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(", ", values);
    }

    /**
     * Completed-job count per technician, read directly against {@code jobs} rather than
     * through a mapped entity: the Jobs domain (FR-05/06/07, the technician PWA) isn't built
     * yet, and this dashboard only ever needs a read-only count from it.
     */
    private Map<UUID, Long> completedJobCounts(List<UUID> technicianIds) {
        if (technicianIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = technicianIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT technician_id, COUNT(*) AS job_count FROM jobs "
                + "WHERE technician_id IN (" + placeholders + ") AND status = 'CLOSED' "
                + "GROUP BY technician_id";
        Map<UUID, Long> counts = new HashMap<>();
        // Bound as UUID objects, not strings — Postgres has no implicit uuid = varchar
        // comparison, so a string-typed parameter fails with "operator does not exist".
        jdbcTemplate.query(sql,
                rs -> { counts.put((UUID) rs.getObject("technician_id"), rs.getLong("job_count")); },
                technicianIds.toArray());
        return counts;
    }
}
