package silver.solutions.techconnect.dto.request.technician;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import silver.solutions.techconnect.entity.JobStatus;

/**
 * Moving a job one step on (FR-06). Only a step allowed from where the job is takes effect;
 * {@code reason} is required for {@code ON_HOLD} and shown to the controller.
 */
public record UpdateJobStatusRequest(@NotNull JobStatus status, @Size(max = 500) String reason) {
}
