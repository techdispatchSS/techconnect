package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.request.controller.JobReportRequest;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.dto.response.controller.JobCountsResponse;
import silver.solutions.techconnect.dto.response.controller.JobDetailResponse;
import silver.solutions.techconnect.dto.response.controller.JobSummaryResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentEventType;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.JobStatusHistory;
import silver.solutions.techconnect.entity.PartUsed;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.IncidentMapper;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.JobStatusHistoryRepository;
import silver.solutions.techconnect.repository.PartUsedRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.util.Strings;

/**
 * A job's life from the moment a technician accepts a dispatch to the controller closing it
 * out (PRD FR-05, FR-06, FR-07).
 *
 * <p>The state machine lives here, not in the screens: a technician moves a job through
 * en route, on site, work started, on hold, resumed and complete; submits a report; and the
 * controller reviews and closes it. Each step is recorded in {@code job_status_history} with
 * who made it, so the trail the controller sees is the same one FR-09 asks to keep.
 *
 * <p>Nothing here knows whether the technician is a person on the technician app or the demo
 * strip: both call {@link #advance} and {@link #submitReport}, so what the controller sees in
 * a demo is what a real job would produce.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** A tab on the controller's job screens. */
    public enum Stage {
        /** Accepted, and the technician is on the way, on site, or finishing up. */
        ACTIVE(EnumSet.of(
                JobStatus.EN_ROUTE, JobStatus.ON_SITE, JobStatus.WORK_STARTED,
                JobStatus.ON_HOLD, JobStatus.WORK_RESUMED, JobStatus.COMPLETE)),
        /** Report submitted; waiting for the controller to review and close it. */
        REVIEW(EnumSet.of(JobStatus.SUBMITTED)),
        CLOSED(EnumSet.of(JobStatus.CLOSED));

        private final Set<JobStatus> statuses;

        Stage(Set<JobStatus> statuses) {
            this.statuses = statuses;
        }

        public Set<JobStatus> statuses() {
            return statuses;
        }
    }

    /** Where a technician can move a job from each status. SUBMITTED and CLOSED have their own steps. */
    private static final Map<JobStatus, Set<JobStatus>> NEXT = new EnumMap<>(JobStatus.class);

    static {
        NEXT.put(JobStatus.EN_ROUTE, EnumSet.of(JobStatus.ON_SITE));
        NEXT.put(JobStatus.ON_SITE, EnumSet.of(JobStatus.WORK_STARTED));
        NEXT.put(JobStatus.WORK_STARTED, EnumSet.of(JobStatus.ON_HOLD, JobStatus.COMPLETE));
        NEXT.put(JobStatus.ON_HOLD, EnumSet.of(JobStatus.WORK_RESUMED));
        NEXT.put(JobStatus.WORK_RESUMED, EnumSet.of(JobStatus.ON_HOLD, JobStatus.COMPLETE));
        NEXT.put(JobStatus.COMPLETE, EnumSet.noneOf(JobStatus.class));
    }

    /** Statuses in which the technician is committed to this job (and so not free for another). */
    private static final Set<JobStatus> TECHNICIAN_OCCUPIED = EnumSet.of(
            JobStatus.EN_ROUTE, JobStatus.ON_SITE, JobStatus.WORK_STARTED,
            JobStatus.ON_HOLD, JobStatus.WORK_RESUMED);

    private final JobRepository jobs;
    private final JobStatusHistoryRepository history;
    private final PartUsedRepository parts;
    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final UserRepository users;
    private final TechnicianProfileRepository profiles;
    private final IncidentEventService events;

    public JobService(
            JobRepository jobs,
            JobStatusHistoryRepository history,
            PartUsedRepository parts,
            IncidentRepository incidents,
            DispatchRepository dispatches,
            UserRepository users,
            TechnicianProfileRepository profiles,
            IncidentEventService events) {
        this.jobs = jobs;
        this.history = history;
        this.parts = parts;
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.users = users;
        this.profiles = profiles;
        this.events = events;
    }

    // --- writes -------------------------------------------------------------------------------

    /**
     * Opens the job for a dispatch that has just been accepted. Runs in the accept's own
     * transaction, so a dispatch is never accepted without a job to follow. A retried accept
     * finds the job already there and changes nothing.
     */
    @Transactional
    public Job openFor(Dispatch dispatch, UUID technicianId) {
        Job existing = jobs.findByDispatchId(dispatch.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        Job job = new Job();
        job.setIncidentId(dispatch.getIncidentId());
        job.setDispatchId(dispatch.getId());
        job.setTechnicianId(technicianId);
        job.setStatus(JobStatus.EN_ROUTE);
        job = jobs.save(job);
        record(job, null, JobStatus.EN_ROUTE, "Dispatch accepted", technicianId);

        // The technician is spoken for. Left alone if they had already set themselves offline.
        profiles.findById(technicianId)
                .filter(p -> p.getStatus() == TechnicianStatus.AVAILABLE)
                .ifPresent(p -> {
                    p.setStatus(TechnicianStatus.ON_JOB);
                    profiles.save(p);
                });
        log.info("Job {} opened for dispatch {} (technician {})", job.getId(), dispatch.getId(), technicianId);
        return job;
    }

    /**
     * Moves a job one step along, as the technician who holds it. The step must be a legal one
     * from where the job is; putting a job on hold needs a reason, which the controller sees.
     */
    @Transactional
    public Job advance(UUID jobId, UUID technicianId, JobStatus to, String reason) {
        Job job = lock(jobId);
        requireHolder(job, technicianId);

        Set<JobStatus> allowed = NEXT.getOrDefault(job.getStatus(), Set.of());
        if (!allowed.contains(to)) {
            throw ApiException.conflict(
                    "A job that is " + label(job.getStatus()) + " cannot move to " + label(to) + ".");
        }
        String note = Strings.blankToNull(Strings.cleanText(reason));
        if (to == JobStatus.ON_HOLD && note == null) {
            throw ApiException.badRequest("Say why the job is being put on hold.");
        }

        Instant now = Instant.now();
        switch (to) {
            case ON_SITE -> job.setArrivedAt(now);
            case WORK_STARTED -> job.setStartedAt(now);
            case COMPLETE -> job.setCompletedAt(now);
            default -> { }
        }
        JobStatus from = job.getStatus();
        job.setStatus(to);
        jobs.save(job);
        record(job, from, to, note, technicianId);

        switch (to) {
            case ON_HOLD -> setIncidentStatus(job.getIncidentId(), IncidentStatus.ON_HOLD);
            case WORK_RESUMED -> setIncidentStatus(job.getIncidentId(), IncidentStatus.IN_PROGRESS);
            case COMPLETE -> freeTechnician(job);
            default -> { }
        }
        return job;
    }

    /** The technician's report on finished work. Only a completed job can be submitted. */
    @Transactional
    public Job submitReport(UUID jobId, UUID technicianId, JobReportRequest report) {
        Job job = lock(jobId);
        requireHolder(job, technicianId);
        if (job.getStatus() != JobStatus.COMPLETE) {
            throw ApiException.conflict("Only a completed job can be submitted. This one is "
                    + label(job.getStatus()) + ".");
        }

        job.setResolutionNotes(Strings.blankToNull(Strings.cleanText(report.resolutionNotes())));
        job.setRootCause(Strings.blankToNull(Strings.cleanText(report.rootCause())));
        job.setRecommendations(Strings.blankToNull(Strings.cleanText(report.recommendations())));
        job.setLabourHours(report.labourHours());
        job.setSubmittedAt(Instant.now());
        job.setStatus(JobStatus.SUBMITTED);
        jobs.save(job);

        if (report.parts() != null) {
            for (JobReportRequest.Part part : report.parts()) {
                PartUsed used = new PartUsed();
                used.setJobId(job.getId());
                used.setPartName(Strings.cleanText(part.name()));
                used.setQuantity(part.quantity());
                used.setSerialNumber(Strings.blankToNull(Strings.cleanText(part.serialNumber())));
                parts.save(used);
            }
        }
        record(job, JobStatus.COMPLETE, JobStatus.SUBMITTED, null, technicianId);
        return job;
    }

    /** The controller's sign-off: the job and its incident are closed. */
    @Transactional
    public Job close(UUID jobId, UUID controllerId, String closingNotes) {
        Job job = lock(jobId);
        if (job.getStatus() != JobStatus.SUBMITTED) {
            throw ApiException.conflict(job.getStatus() == JobStatus.CLOSED
                    ? "This job is already closed."
                    : "Only a submitted job can be closed. This one is " + label(job.getStatus()) + ".");
        }
        Instant now = Instant.now();
        job.setStatus(JobStatus.CLOSED);
        job.setClosedBy(controllerId);
        job.setClosedAt(now);
        job.setClosingNotes(Strings.blankToNull(Strings.cleanText(closingNotes)));
        jobs.save(job);
        record(job, JobStatus.SUBMITTED, JobStatus.CLOSED, job.getClosingNotes(), controllerId);

        incidents.findByIdForUpdate(job.getIncidentId()).ifPresent(incident -> {
            if (incident.getStatus() != IncidentStatus.CLOSED) {
                incident.setStatus(IncidentStatus.CLOSED);
                incident.setClosedAt(now);
                incidents.save(incident);
                events.record(incident.getId(), IncidentEventType.INCIDENT_CLOSED, controllerId,
                        job.getDispatchId(), "Closed after review");
            }
        });
        return job;
    }

    // --- reads --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<JobSummaryResponse> list(Stage stage, String query, int page, int size) {
        // The order is in the query (it sorts on the incident's SLA, which the job does not have).
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));

        Page<Job> found = jobs.search(stage.statuses(), likePattern(query), pageable);
        Lookups lookups = lookups(found.getContent());
        return PageResponse.of(found, job -> summary(job, lookups));
    }

    @Transactional(readOnly = true)
    public JobCountsResponse counts() {
        return new JobCountsResponse(
                jobs.countByStatusIn(Stage.ACTIVE.statuses()),
                jobs.countByStatusIn(Stage.REVIEW.statuses()),
                jobs.countByStatusIn(Stage.CLOSED.statuses()));
    }

    @Transactional(readOnly = true)
    public JobDetailResponse detail(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found"));
        Incident incident = incidents.findById(job.getIncidentId()).orElseThrow();
        Dispatch dispatch = job.getDispatchId() == null
                ? null
                : dispatches.findById(job.getDispatchId()).orElse(null);

        List<JobStatusHistory> trail = history.findByJobIdOrderByChangedAtDesc(jobId).reversed();
        Set<UUID> peopleIds = trail.stream().map(JobStatusHistory::getChangedBy).collect(Collectors.toSet());
        peopleIds.add(job.getTechnicianId());
        if (job.getClosedBy() != null) {
            peopleIds.add(job.getClosedBy());
        }
        Map<UUID, String> names = users.findAllById(peopleIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        Lookups lookups = new Lookups(Map.of(incident.getId(), incident), names);

        return new JobDetailResponse(
                summary(job, lookups),
                incident.getContactName(),
                incident.getContactPhone(),
                incident.getIssueType(),
                incident.getDescription(),
                dispatch == null ? null : dispatch.getJobType(),
                dispatch == null ? null : dispatch.getNotesForTechnician(),
                job.getResolutionNotes(),
                job.getRootCause(),
                job.getRecommendations(),
                job.getLabourHours(),
                parts.findByJobId(jobId).stream()
                        .sorted(Comparator.comparing(PartUsed::getCreatedAt))
                        .map(p -> new JobDetailResponse.Part(
                                p.getPartName(), p.getQuantity(), p.getSerialNumber()))
                        .toList(),
                job.getClosingNotes(),
                job.getClosedBy() == null ? null : names.get(job.getClosedBy()),
                trail.stream()
                        .map(h -> new JobDetailResponse.HistoryEntry(
                                h.getStatus(), h.getPreviousStatus(), h.getReason(),
                                h.getChangedBy(), names.getOrDefault(h.getChangedBy(), "Unknown"),
                                h.getChangedAt()))
                        .toList(),
                job.getStatus() == JobStatus.SUBMITTED);
    }

    /** The job itself, for callers acting on a technician's behalf (the demo strip). */
    @Transactional(readOnly = true)
    public Job holderOf(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found"));
    }

    /** The job behind a dispatch, if it has been accepted. */
    @Transactional(readOnly = true)
    public Job forDispatch(UUID dispatchId) {
        return jobs.findByDispatchId(dispatchId).orElse(null);
    }

    // --- internals ----------------------------------------------------------------------------

    private Job lock(UUID jobId) {
        return jobs.findByIdForUpdate(jobId).orElseThrow(() -> ApiException.notFound("Job not found"));
    }

    /** Someone else's job is the same "not found" as one that does not exist. */
    private static void requireHolder(Job job, UUID technicianId) {
        if (!job.getTechnicianId().equals(technicianId)) {
            throw ApiException.notFound("Job not found");
        }
    }

    private void record(Job job, JobStatus previous, JobStatus status, String reason, UUID by) {
        JobStatusHistory entry = new JobStatusHistory();
        entry.setJobId(job.getId());
        entry.setPreviousStatus(previous);
        entry.setStatus(status);
        entry.setReason(reason);
        entry.setChangedBy(by);
        history.save(entry);
    }

    private void setIncidentStatus(UUID incidentId, IncidentStatus status) {
        incidents.findByIdForUpdate(incidentId).ifPresent(incident -> {
            if (incident.getStatus() != IncidentStatus.CLOSED && incident.getStatus() != status) {
                incident.setStatus(status);
                incidents.save(incident);
            }
        });
    }

    /** Back to AVAILABLE once the work is done, unless they are still committed elsewhere. */
    private void freeTechnician(Job job) {
        if (jobs.existsByTechnicianIdAndStatusIn(job.getTechnicianId(), TECHNICIAN_OCCUPIED)) {
            return;
        }
        profiles.findById(job.getTechnicianId())
                .filter(p -> p.getStatus() == TechnicianStatus.ON_JOB)
                .ifPresent((TechnicianProfile p) -> {
                    p.setStatus(TechnicianStatus.AVAILABLE);
                    profiles.save(p);
                });
    }

    /** The incidents and people a page of jobs refers to, fetched once rather than per row. */
    private record Lookups(Map<UUID, Incident> incidents, Map<UUID, String> names) {
    }

    private Lookups lookups(Collection<Job> page) {
        if (page.isEmpty()) {
            return new Lookups(Map.of(), Map.of());
        }
        Map<UUID, Incident> byId = new HashMap<>();
        incidents.findAllById(page.stream().map(Job::getIncidentId).collect(Collectors.toSet()))
                .forEach(i -> byId.put(i.getId(), i));
        Map<UUID, String> names = users
                .findAllById(page.stream().map(Job::getTechnicianId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        return new Lookups(byId, names);
    }

    private JobSummaryResponse summary(Job job, Lookups lookups) {
        Incident incident = lookups.incidents().get(job.getIncidentId());
        return new JobSummaryResponse(
                job.getId(),
                job.getIncidentId(),
                incident == null ? null : IncidentMapper.ticketRef(incident),
                incident == null ? null : incident.getClientName(),
                incident == null ? null : incident.getSiteAddress(),
                incident == null ? null : incident.getPriority(),
                incident == null ? null : incident.getSlaDueAt(),
                incident != null && incident.isOverdue(),
                job.getTechnicianId(),
                lookups.names().getOrDefault(job.getTechnicianId(), "Unknown"),
                job.getStatus(),
                job.getCreatedAt(),
                job.getArrivedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getSubmittedAt(),
                job.getClosedAt());
    }

    /** A lower-case {@code like} pattern for a search box, or empty for none. */
    private static String likePattern(String query) {
        String q = Strings.blankToNull(Strings.cleanText(query));
        if (q == null) {
            return "";
        }
        String escaped = q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** How a status reads in a message: {@code ON_HOLD} becomes "on hold". */
    private static String label(JobStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }
}
