package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.dto.response.controller.TechnicianRosterEntry;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianSkill;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.mapper.IncidentMapper;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.TechnicianSkillRepository;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.util.Strings;

/**
 * The controller's read-only view of the technicians: who they are, whether they are free, what
 * they are on, and what they can do. Managing them (adding, editing, offboarding) stays with the
 * Manager; this is for the controller to know who to send a job to.
 *
 * <p>The roster is filtered and paged in memory. A fleet of technicians is hundreds, not
 * millions, and the page needs their skills, profile and current job together; one pass over
 * the role is simpler and cheaper than a join across four tables for every keystroke.
 */
@Service
public class TechnicianRosterService {

    private static final Set<JobStatus> IN_HAND = EnumSet.of(
            JobStatus.EN_ROUTE, JobStatus.ON_SITE, JobStatus.WORK_STARTED,
            JobStatus.ON_HOLD, JobStatus.WORK_RESUMED, JobStatus.COMPLETE);

    private final UserRepository users;
    private final TechnicianProfileRepository profiles;
    private final TechnicianSkillRepository skills;
    private final JobRepository jobs;
    private final IncidentRepository incidents;

    public TechnicianRosterService(
            UserRepository users,
            TechnicianProfileRepository profiles,
            TechnicianSkillRepository skills,
            JobRepository jobs,
            IncidentRepository incidents) {
        this.users = users;
        this.profiles = profiles;
        this.skills = skills;
        this.jobs = jobs;
        this.incidents = incidents;
    }

    @Transactional(readOnly = true)
    public PageResponse<TechnicianRosterEntry> list(
            String query, TechnicianStatus availability, boolean includeDisabled, int page, int size) {

        String q = Strings.blankToNull(Strings.cleanText(query));
        String needle = q == null ? null : q.toLowerCase(Locale.ROOT);

        List<User> technicians = users.findByRole(UserRole.TECHNICIAN);
        Map<UUID, TechnicianProfile> profileById = profiles
                .findAllById(technicians.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(TechnicianProfile::getUserId, p -> p));
        Map<UUID, List<String>> skillsById = skills
                .findByUserIdIn(technicians.stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(TechnicianSkill::getUserId,
                        Collectors.mapping(TechnicianSkill::getSkill, Collectors.toList())));

        List<User> matching = technicians.stream()
                .filter(u -> includeDisabled || u.getStatus() != UserStatus.DISABLED)
                .filter(u -> availability == null
                        || availabilityOf(profileById.get(u.getId())) == availability)
                .filter(u -> needle == null || matches(u, skillsById.get(u.getId()), needle))
                // Free technicians first: that is who the controller is looking for.
                .sorted(Comparator
                        .comparing((User u) -> availabilityOf(profileById.get(u.getId())).ordinal())
                        .thenComparing(u -> u.getName().toLowerCase(Locale.ROOT)))
                .toList();

        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);
        int from = Math.min(pageIndex * pageSize, matching.size());
        List<User> slice = matching.subList(from, Math.min(from + pageSize, matching.size()));

        List<UUID> ids = slice.stream().map(User::getId).toList();
        Map<UUID, Job> current = new HashMap<>();
        Map<UUID, Long> closedCount = new HashMap<>();
        Map<UUID, Incident> incidentById = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Job job : jobs.findByTechnicianIdInAndStatusIn(ids, EnumSet.allOf(JobStatus.class))) {
                if (job.getStatus() == JobStatus.CLOSED) {
                    closedCount.merge(job.getTechnicianId(), 1L, Long::sum);
                } else if (IN_HAND.contains(job.getStatus())) {
                    current.merge(job.getTechnicianId(), job,
                            (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b);
                }
            }
            incidents.findAllById(current.values().stream().map(Job::getIncidentId).toList())
                    .forEach(i -> incidentById.put(i.getId(), i));
        }

        List<TechnicianRosterEntry> content = slice.stream()
                .map(u -> entry(u, profileById.get(u.getId()), skillsById.get(u.getId()),
                        current.get(u.getId()), incidentById, closedCount.getOrDefault(u.getId(), 0L)))
                .toList();

        int totalPages = (int) Math.ceil(matching.size() / (double) pageSize);
        return new PageResponse<>(content, pageIndex, pageSize, matching.size(), totalPages);
    }

    private static TechnicianStatus availabilityOf(TechnicianProfile profile) {
        return profile == null ? TechnicianStatus.OFFLINE : profile.getStatus();
    }

    private static boolean matches(User user, List<String> skills, String needle) {
        if (user.getName().toLowerCase(Locale.ROOT).contains(needle)
                || user.getEmail().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (user.getAddress() != null && user.getAddress().getCity() != null
                && user.getAddress().getCity().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return skills != null && skills.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static TechnicianRosterEntry entry(
            User user,
            TechnicianProfile profile,
            List<String> skills,
            Job current,
            Map<UUID, Incident> incidentById,
            long closed) {

        TechnicianRosterEntry.CurrentJob currentJob = null;
        if (current != null) {
            Incident incident = incidentById.get(current.getIncidentId());
            currentJob = new TechnicianRosterEntry.CurrentJob(
                    current.getId(),
                    incident == null ? null : IncidentMapper.ticketRef(incident),
                    incident == null ? null : incident.getClientName(),
                    current.getStatus());
        }
        var address = user.getAddress();
        Instant seen = profile == null ? null : profile.getLastLocationAt();
        return new TechnicianRosterEntry(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                availabilityOf(profile),
                address == null ? null : address.getCity(),
                address == null ? null : address.getProvince(),
                address == null ? null : address.countryOrDefault().name(),
                skills == null ? List.of() : skills.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                currentJob,
                closed,
                seen);
    }
}
