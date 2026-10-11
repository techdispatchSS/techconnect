package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchType;

/** Sizes mirror the {@code dispatches} column widths, so an over-long value is a 400, not a 500. */
public record CreateDispatchRequest(
        @NotNull UUID incidentId,
        @NotNull DispatchType dispatchType,
        @Size(max = 64) String jobType,
        List<String> requiredSkills,
        List<String> requiredCertifications,
        @Size(max = 64) String slaResponse,
        @Size(max = 255) String siteContact,
        @Size(max = 4000) String notesForTechnician,
        @NotEmpty List<UUID> technicianIds) {
}
