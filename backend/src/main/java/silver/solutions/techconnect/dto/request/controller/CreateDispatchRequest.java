package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchType;

public record CreateDispatchRequest(
        @NotNull UUID incidentId,
        @NotNull DispatchType dispatchType,
        String jobType,
        List<String> requiredSkills,
        List<String> requiredCertifications,
        String slaResponse,
        String siteContact,
        String notesForTechnician,
        @NotEmpty List<UUID> technicianIds) {
}
