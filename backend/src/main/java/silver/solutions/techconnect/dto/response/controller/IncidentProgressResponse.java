package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.DispatchType;

/**
 * The controller dashboard's "ticket progress" view: an incident already in progress, plus
 * its most recent dispatch and how every invited technician has responded so far.
 *
 * <p>There is deliberately no on-site job timeline (en route / on site / work started) here —
 * the technician app that would write those events (FR-05/06/07) doesn't exist yet, so there
 * is nothing real to show beyond dispatch and response state. See {@code jobs} /
 * {@code job_status_history} in {@code V1__init.sql} for the schema this will read from once
 * that side is built.
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
        String acceptedTechnicianName) {
}
