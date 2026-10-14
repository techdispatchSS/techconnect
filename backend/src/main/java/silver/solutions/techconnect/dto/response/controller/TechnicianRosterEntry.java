package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.UserStatus;

/** One technician on the controller's read-only roster. */
public record TechnicianRosterEntry(
        UUID id,
        String name,
        String email,
        String phone,
        UserStatus accountStatus,
        TechnicianStatus availability,
        String city,
        String region,
        String country,
        List<String> skills,
        /** The job they are on right now, if any. */
        CurrentJob currentJob,
        long jobsClosed,
        /** When their app last reported a position; null until the technician app exists. */
        Instant lastSeenAt) {

    public record CurrentJob(UUID jobId, String ticketRef, String clientName, JobStatus status) {
    }
}
