package silver.solutions.techconnect.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.technician.TechnicianAvailabilityResponse;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;

/**
 * Whether a technician can be sent a job (FR-04/FR-05, PRD §8.5). The technician moves
 * themselves between {@code OFFLINE} and {@code AVAILABLE} (T-14); {@code ON_JOB} and back is
 * {@link JobService}'s to do, on accept and on finishing. While they hold a job that is not
 * done they can't change it themselves, and the dispatch engine won't send them another.
 */
@Service
public class TechnicianAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(TechnicianAvailabilityService.class);

    /** The columns are NUMERIC(9, 6): about 10 cm, far finer than a phone fix. */
    private static final int COORDINATE_SCALE = 6;

    private final TechnicianProfileRepository profiles;
    private final JobRepository jobs;

    public TechnicianAvailabilityService(TechnicianProfileRepository profiles, JobRepository jobs) {
        this.profiles = profiles;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public TechnicianAvailabilityResponse current(UUID technicianId) {
        TechnicianProfile profile = profiles.findById(technicianId)
                .orElseGet(() -> TechnicianProfile.forUser(technicianId));
        return toResponse(profile, !busy(Set.of(technicianId)).isEmpty());
    }

    /** Goes on or off duty. Asking for the state they are already in changes nothing. */
    @Transactional
    public TechnicianAvailabilityResponse setAvailable(UUID technicianId, boolean available) {
        TechnicianProfile profile = lockedProfile(technicianId);
        boolean onJob = !busy(Set.of(technicianId)).isEmpty();
        TechnicianStatus wanted = available ? TechnicianStatus.AVAILABLE : TechnicianStatus.OFFLINE;

        if (profile.getStatus() != wanted) {
            if (onJob) {
                throw ApiException.conflict(available
                        ? "You're on a job. You'll be available again once it's complete."
                        : "Finish your current job before going off duty.");
            }
            TechnicianStatus previous = profile.getStatus();
            profile.setStatus(wanted);
            profiles.save(profile);
            log.info("Technician {} availability {} -> {}", technicianId, previous, wanted);
        }
        return toResponse(profile, onJob);
    }

    @Transactional
    public TechnicianAvailabilityResponse reportLocation(
            UUID technicianId, BigDecimal latitude, BigDecimal longitude) {
        TechnicianProfile profile = lockedProfile(technicianId);
        profile.setLastLatitude(latitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
        profile.setLastLongitude(longitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
        profile.setLastLocationAt(Instant.now());
        profiles.save(profile);
        return toResponse(profile, !busy(Set.of(technicianId)).isEmpty());
    }

    /** Of these technicians, the ones holding a job that is not done yet. */
    @Transactional(readOnly = true)
    public Set<UUID> busy(Collection<UUID> technicianIds) {
        return jobs.findByTechnicianIdInAndStatusIn(technicianIds, JobService.TECHNICIAN_OCCUPIED)
                .stream()
                .map(Job::getTechnicianId)
                .collect(Collectors.toSet());
    }

    /** Every technician is provisioned with a profile; one predating that gets it on first use. */
    private TechnicianProfile lockedProfile(UUID technicianId) {
        return profiles.findByIdForUpdate(technicianId)
                .orElseGet(() -> profiles.save(TechnicianProfile.forUser(technicianId)));
    }

    private static TechnicianAvailabilityResponse toResponse(TechnicianProfile profile, boolean onJob) {
        return new TechnicianAvailabilityResponse(
                profile.getStatus(),
                onJob,
                profile.getLastLatitude(),
                profile.getLastLongitude(),
                profile.getLastLocationAt());
    }
}
