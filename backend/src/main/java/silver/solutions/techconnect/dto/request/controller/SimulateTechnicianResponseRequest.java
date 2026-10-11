package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import silver.solutions.techconnect.entity.DispatchResponseStatus;

/** Simulation only: answer a dispatch as one of its invited technicians. */
public record SimulateTechnicianResponseRequest(
        @NotNull UUID technicianId,
        /** {@code ACCEPTED} or {@code DECLINED}. */
        @NotNull DispatchResponseStatus response) {
}
