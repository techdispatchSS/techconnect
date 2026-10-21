package silver.solutions.techconnect.dto.response.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.JobStatus;

/**
 * Everything the controller needs to follow a job and, once the technician has submitted it,
 * to review and close it: the incident, the technician's report, and the full status history.
 */
public record JobDetailResponse(
        JobSummaryResponse job,
        String contactName,
        String contactPhone,
        String issueType,
        String description,
        String jobType,
        String notesForTechnician,
        String resolutionNotes,
        String rootCause,
        String recommendations,
        BigDecimal labourHours,
        List<Part> parts,
        String closingNotes,
        String closedByName,
        /** Oldest first, so it reads as a timeline. */
        List<HistoryEntry> history,
        /** Whether the controller can close it now: the technician has submitted their report. */
        boolean canClose) {

    public record Part(String name, int quantity, String serialNumber) {
    }

    public record HistoryEntry(
            JobStatus status,
            JobStatus previousStatus,
            String reason,
            UUID changedById,
            String changedByName,
            Instant changedAt) {
    }
}
