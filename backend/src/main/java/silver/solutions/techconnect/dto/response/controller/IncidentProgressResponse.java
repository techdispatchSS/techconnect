package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.DispatchType;
import silver.solutions.techconnect.entity.JobStatus;

/**
 * The controller dashboard's "ticket progress" view: an incident already in progress, plus
 * its most recent dispatch and how every invited technician has responded so far. Once the
 * dispatch is accepted it also names the job; the job's own timeline is on its page.
 */
public record IncidentProgressResponse(
        IncidentResponse incident,
        UUID dispatchId,
        DispatchType dispatchType,
        DispatchStatus dispatchStatus,
        String jobType,
        List<String> requiredSkills,
        List<String> requiredCertifications,
        String slaResponse,
        String siteContact,
        String notesForTechnician,
        Instant dispatchCreatedAt,
        Instant dispatchExpiresAt,
        List<DispatchResponseEntry> responses,
        /** The invited technician who accepted, if any — {@code null} while still pending or
         * if the dispatch expired with no acceptance. */
        String acceptedTechnicianName,
        /** The job opened when it was accepted, and where it stands; null before that. */
        UUID jobId,
        JobStatus jobStatus) {
}
