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
import silver.solutions.techconnect.dto.request.controller.CreateDispatchRequest;
import silver.solutions.techconnect.dto.response.controller.DispatchCreateResponse;
import silver.solutions.techconnect.dto.response.controller.TechnicianCandidateResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
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

    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final DispatchResponseRepository dispatchResponses;
    private final UserRepository users;
    private final TechnicianProfileRepository technicianProfiles;
    private final TechnicianSkillRepository technicianSkills;
    private final DispatchMapper dispatchMapper;
    private final JdbcTemplate jdbcTemplate;

    public DispatchService(
            IncidentRepository incidents,
            DispatchRepository dispatches,
            DispatchResponseRepository dispatchResponses,
            UserRepository users,
            TechnicianProfileRepository technicianProfiles,
            TechnicianSkillRepository technicianSkills,
            DispatchMapper dispatchMapper,
            JdbcTemplate jdbcTemplate) {
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.dispatchResponses = dispatchResponses;
        this.users = users;
        this.technicianProfiles = technicianProfiles;
        this.technicianSkills = technicianSkills;
        this.dispatchMapper = dispatchMapper;
        this.jdbcTemplate = jdbcTemplate;
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
        Incident incident = incidents.findById(request.incidentId())
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        if (request.technicianIds().isEmpty()) {
            throw ApiException.badRequest("Select at least one technician.");
        }

        List<User> technicians = users.findAllById(request.technicianIds());
        if (technicians.size() != request.technicianIds().size()) {
            throw ApiException.badRequest("One or more selected technicians could not be found.");
        }
        if (!technicians.stream().allMatch(u -> u.getRole() == UserRole.TECHNICIAN)) {
            throw ApiException.badRequest("Only technicians can be dispatched to a job.");
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
            incidents.save(incident);
        }

        log.info("Dispatch {} ({}) created for incident {} by {} — {} technician(s) invited",
                dispatch.getId(), dispatch.getDispatchType(), incident.getId(), actorId,
                request.technicianIds().size());

        return dispatchMapper.toCreateResponse(dispatch, request.technicianIds());
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
