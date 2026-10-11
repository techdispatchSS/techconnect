package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.DispatchType;

public record DispatchCreateResponse(
        UUID id,
        UUID incidentId,
        DispatchType dispatchType,
        DispatchStatus status,
        Instant expiresAt,
        List<UUID> invitedTechnicianIds) {
}
